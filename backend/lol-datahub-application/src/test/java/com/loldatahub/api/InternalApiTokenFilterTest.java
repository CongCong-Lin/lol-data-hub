package com.loldatahub.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

class InternalApiTokenFilterTest {

    private static final String VALID_TOKEN = "test-secret-token-12345";
    private static final String TOKEN_HEADER = "X-Internal-Token";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    private final FilterChain filterChain = mock(FilterChain.class);

    private InternalApiTokenFilter filter;
    private InternalApiTokenFilter emptyTokenFilter;

    @BeforeEach
    void setUp() {
        filter = new InternalApiTokenFilter(VALID_TOKEN, objectMapper);
        emptyTokenFilter = new InternalApiTokenFilter("", objectMapper);
    }

    // ---- 未配置 Token 时返回 503 ----

    @Test
    void returnsServiceUnavailableWhenTokenNotConfigured() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        emptyTokenFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        assertErrorResponse(response, "内部接口未配置访问令牌");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void returnsServiceUnavailableForNestedPathWhenTokenNotConfigured() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/collections/heroes");
        MockHttpServletResponse response = new MockHttpServletResponse();

        emptyTokenFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ---- 请求头缺失返回 401 ----

    @Test
    void returnsUnauthorizedWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertErrorResponse(response, "内部接口访问令牌无效");
        verify(filterChain, never()).doFilter(request, response);
    }

    // ---- 错误令牌返回 401 ----

    @Test
    void returnsUnauthorizedWhenTokenMismatch() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertErrorResponse(response, "内部接口访问令牌无效");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void returnsUnauthorizedWhenTokenEmpty() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, "");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    // ---- 正确令牌放行 ----

    @Test
    void passesThroughWhenTokenValid() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void passesThroughForNestedInternalPath() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/collections/heroes");
        request.addHeader(TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ---- 公开接口放行（不受 Token 影响） ----

    @Test
    void publicApiPathBypassesFilter() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/api/v1/catalog/seasons");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void publicStatisticsPathBypassesFilter() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/api/v1/statistics/champions");
        request.setParameter("seasonId", "237");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ---- Actuator 放行 ----

    @Test
    void actuatorHealthBypassesFilter() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void actuatorInfoBypassesFilter() throws Exception {
        MockHttpServletRequest request = createRequest("GET", "/actuator/info");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ---- /api/internal 精确路径也被拦截 ----

    @Test
    void exactInternalPathIsIntercepted() throws Exception {
        MockHttpServletRequest request = createInternalRequest("GET", "/api/internal");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void exactInternalPathWithValidTokenPasses() throws Exception {
        MockHttpServletRequest request = createInternalRequest("GET", "/api/internal");
        request.addHeader(TOKEN_HEADER, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    // ---- 响应 Content-Type 和 JSON 结构 ----

    @Test
    void errorResponseHasJsonContentType() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
    }

    @Test
    void errorResponseHasUnifiedJsonStructure() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("message", "内部接口访问令牌无效");
        assertThat(body).doesNotContainKey("data");
    }

    @Test
    void serviceUnavailableResponseHasUnifiedJsonStructure() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        MockHttpServletResponse response = new MockHttpServletResponse();

        emptyTokenFilter.doFilter(request, response, filterChain);

        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("message", "内部接口未配置访问令牌");
        assertThat(body).doesNotContainKey("data");
    }

    // ---- 常量时间比较边界场景 ----

    @Test
    void rejectsTokenWithSimilarPrefix() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, VALID_TOKEN + "x");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void rejectsShorterTokenWithSamePrefix() throws Exception {
        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, VALID_TOKEN.substring(0, 10));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ---- Unicode 令牌比较 ----

    @Test
    void handlesUnicodeTokenCorrectly() throws Exception {
        String unicodeToken = "内部令牌-测试-🔐";
        InternalApiTokenFilter unicodeFilter = new InternalApiTokenFilter(unicodeToken, objectMapper);

        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, unicodeToken);
        MockHttpServletResponse response = new MockHttpServletResponse();

        unicodeFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsDifferentUnicodeToken() throws Exception {
        String unicodeToken = "内部令牌-测试-🔐";
        InternalApiTokenFilter unicodeFilter = new InternalApiTokenFilter(unicodeToken, objectMapper);

        MockHttpServletRequest request = createInternalRequest("POST", "/api/internal/catalog/sync");
        request.addHeader(TOKEN_HEADER, "内部令牌-测试-🔑");
        MockHttpServletResponse response = new MockHttpServletResponse();

        unicodeFilter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    // ---- 辅助方法 ----

    /**
     * 创建内部接口请求（servletPath 与 requestURI 一致）。
     */
    private MockHttpServletRequest createInternalRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    /**
     * 创建普通请求（servletPath 与 requestURI 一致）。
     */
    private MockHttpServletRequest createRequest(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }

    private void assertErrorResponse(MockHttpServletResponse response, String expectedMessage) throws IOException {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(response.getContentAsString(), Map.class);
        assertThat(body).containsEntry("success", false);
        assertThat(body).containsEntry("message", expectedMessage);
    }
}
