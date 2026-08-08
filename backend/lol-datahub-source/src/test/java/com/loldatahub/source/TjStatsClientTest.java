package com.loldatahub.source;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TjStatsClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private RestClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    // ══════════════════════════════════════════════════════════════════════
    // 不可重试状态码：立即失败，不重试
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void http401ThrowsImmediatelyWithoutRetry() {
        RestClientResponseException exception = new RestClientResponseException(
                "Unauthorized", 401, "Unauthorized", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("HTTP 401")
                .hasCause(exception);

        // 验证只发起了一次请求
        verify(restClient, times(1)).get();
    }

    @Test
    void http400ThrowsImmediatelyWithoutRetry() {
        RestClientResponseException exception = new RestClientResponseException(
                "Bad Request", 400, "Bad Request", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("HTTP 400")
                .hasCause(exception);

        verify(restClient, times(1)).get();
    }

    @Test
    void http403ThrowsImmediatelyWithoutRetry() {
        RestClientResponseException exception = new RestClientResponseException(
                "Forbidden", 403, "Forbidden", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("HTTP 403")
                .hasCause(exception);

        verify(restClient, times(1)).get();
    }

    @Test
    void http404ThrowsImmediatelyWithoutRetry() {
        RestClientResponseException exception = new RestClientResponseException(
                "Not Found", 404, "Not Found", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("HTTP 404")
                .hasCause(exception);

        verify(restClient, times(1)).get();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 可重试状态码：最多重试 3 次后失败
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void http503RetriesUpToThreeTimesThenFails() {
        RestClientResponseException exception = new RestClientResponseException(
                "Service Unavailable", 503, "Service Unavailable", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("重试 3 次")
                .hasCause(exception);

        // 验证发起了三次请求
        verify(restClient, times(3)).get();
    }

    @Test
    void http429RetriesUpToThreeTimesThenFails() {
        RestClientResponseException exception = new RestClientResponseException(
                "Too Many Requests", 429, "Too Many Requests", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("重试 3 次")
                .hasCause(exception);

        verify(restClient, times(3)).get();
    }

    @Test
    void http408RetriesUpToThreeTimesThenFails() {
        RestClientResponseException exception = new RestClientResponseException(
                "Request Timeout", 408, "Request Timeout", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("重试 3 次")
                .hasCause(exception);

        verify(restClient, times(3)).get();
    }

    @Test
    void http500RetriesUpToThreeTimesThenFails() {
        RestClientResponseException exception = new RestClientResponseException(
                "Internal Server Error", 500, "Internal Server Error", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("重试 3 次")
                .hasCause(exception);

        verify(restClient, times(3)).get();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 连接/读取类异常：继续有限重试
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void connectionErrorRetriesUpToThreeTimesThenFails() {
        RestClientException exception = new RestClientException("Connection refused");

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(exception);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("重试 3 次")
                .hasCause(exception);

        verify(restClient, times(3)).get();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 成功响应：正常返回
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void successfulResponseReturnsBody() {
        String expectedBody = "{\"success\": true, \"data\": []}";

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenReturn(expectedBody);

        TjStatsClient client = new TjStatsClient(restClient);

        String result = client.fetchSeasons();

        assertThat(result).isEqualTo(expectedBody);
        verify(restClient, times(1)).get();
    }

    // ══════════════════════════════════════════════════════════════════════
    // 异常消息不含 Authorization
    // ══════════════════════════════════════════════════════════════════════

    @Test
    void exceptionMessagesDoNotContainAuthorization() {
        // 不可重试状态码
        RestClientResponseException nonRetryable = new RestClientResponseException(
                "Unauthorized", 401, "Unauthorized", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(nonRetryable);

        TjStatsClient client = new TjStatsClient(restClient);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Authorization"));

        // 可重试状态码
        reset(restClient, requestHeadersUriSpec, requestHeadersSpec, responseSpec);
        RestClientResponseException retryable = new RestClientResponseException(
                "Service Unavailable", 503, "Service Unavailable", null, null, null);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString(), any(Object[].class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(String.class)).thenThrow(retryable);

        assertThatThrownBy(() -> client.fetchSeasons())
                .isInstanceOf(TjStatsSourceException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("Authorization"));
    }
}
