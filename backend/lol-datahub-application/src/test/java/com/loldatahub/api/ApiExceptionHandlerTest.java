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

    @Test
    void mapsUnreadableBodyToBadRequest() {
        var response = handler.unreadableMessage();

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("请求体格式错误"));
    }

    @Test
    void mapsUnsupportedContentTypeToUnsupportedMediaType() {
        var response = handler.unsupportedMediaType();

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("请求内容类型不支持"));
    }

    @Test
    void mapsUnacceptableResponseTypeToNotAcceptable() {
        var response = handler.mediaTypeNotAcceptable();

        assertThat(response.getStatusCode().value()).isEqualTo(406);
        assertThat(response.getBody()).isEqualTo(ApiResponse.failure("无法生成客户端要求的响应类型"));
    }
}
