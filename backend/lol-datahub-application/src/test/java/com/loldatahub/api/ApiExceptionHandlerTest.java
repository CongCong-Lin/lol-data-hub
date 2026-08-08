package com.loldatahub.api;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsMissingEndpointToNotFound() {
        var response = handler.notFound();

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("接口不存在"));
    }

    @Test
    void mapsUnsupportedMethodToMethodNotAllowed() {
        var response = handler.methodNotAllowed();

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("请求方法不支持"));
    }
}
