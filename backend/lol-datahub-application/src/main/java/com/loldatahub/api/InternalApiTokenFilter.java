package com.loldatahub.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 内部接口 Token 鉴权过滤器。
 * <p>
 * 仅拦截 {@code /api/internal} 及 {@code /api/internal/**} 路径，
 * 通过 {@code X-Internal-Token} 请求头验证访问令牌。
 */
public class InternalApiTokenFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/api/internal";

    private final String configuredToken;
    private final ObjectMapper objectMapper;

    public InternalApiTokenFilter(String configuredToken, ObjectMapper objectMapper) {
        this.configuredToken = configuredToken;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();
        if (!isInternalApiPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Token 未配置 → 503
        if (configuredToken == null || configuredToken.isEmpty()) {
            writeError(response, 503, "内部接口未配置访问令牌");
            return;
        }

        String requestToken = request.getHeader(TOKEN_HEADER);

        // 请求头缺失或不匹配 → 401
        if (requestToken == null || !constantTimeEquals(configuredToken, requestToken)) {
            writeError(response, 401, "内部接口访问令牌无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 判断路径是否为内部接口（精确匹配 /api/internal 或前缀 /api/internal/）。
     */
    private boolean isInternalApiPath(String path) {
        return path.equals(INTERNAL_PATH_PREFIX)
                || path.startsWith(INTERNAL_PATH_PREFIX + "/");
    }

    /**
     * UTF-8 字节 + MessageDigest.isEqual 做常量时间比较，防止时序攻击。
     */
    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }

    /**
     * 写入统一格式的 JSON 错误响应。
     */
    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.failure(message)));
    }
}
