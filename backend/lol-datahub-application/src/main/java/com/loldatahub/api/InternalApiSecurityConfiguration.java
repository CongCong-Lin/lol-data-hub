package com.loldatahub.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部接口 Token 鉴权配置。
 * <p>
 * 通过 {@code lol-datahub.security.internal-api-token} 读取令牌，
 * 环境变量 {@code INTERNAL_API_TOKEN} 映射到该配置项。
 * 令牌为空时内部接口返回 503，不允许放行。
 */
@Configuration
public class InternalApiSecurityConfiguration {

    /**
     * 配置属性绑定。
     */
    @Bean
    @ConfigurationProperties("lol-datahub.security")
    InternalApiSecurityProperties internalApiSecurityProperties() {
        return new InternalApiSecurityProperties();
    }

    /**
     * 注册 Token 鉴权过滤器，仅拦截 /api/internal 路径。
     */
    @Bean
    FilterRegistrationBean<InternalApiTokenFilter> internalApiTokenFilterRegistration(
            InternalApiSecurityProperties properties, ObjectMapper objectMapper) {

        String token = properties.getInternalApiToken() == null ? "" : properties.getInternalApiToken();

        FilterRegistrationBean<InternalApiTokenFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new InternalApiTokenFilter(token, objectMapper));
        registration.addUrlPatterns("/api/internal", "/api/internal/*");
        registration.setOrder(-100);
        registration.setName("internalApiTokenFilter");
        return registration;
    }

    /**
     * 安全配置属性。
     */
    public static class InternalApiSecurityProperties {
        private String internalApiToken;

        public String getInternalApiToken() {
            return internalApiToken;
        }

        public void setInternalApiToken(String internalApiToken) {
            // Spring 环境变量绑定空字符串时视为未配置
            this.internalApiToken = (internalApiToken != null && internalApiToken.isBlank())
                    ? null : internalApiToken;
        }
    }
}
