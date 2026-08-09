package com.loldatahub.source;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class TjStatsClient {
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;

    public TjStatsClient(RestClient tjStatsRestClient) {
        this.restClient = tjStatsRestClient;
    }

    public String fetchSeasons() {
        return get("/schedule/season");
    }

    public String fetchStages(long seasonId) {
        return get("/schedule/stage?seasonId={seasonId}", seasonId);
    }

    public String fetchHeroStatistics(long seasonId, long stageId) {
        return get("/compound/public/hero?seasonId={seasonId}&stageIds={stageId}", seasonId, stageId);
    }

    public String fetchTeamStatistics(long seasonId, long stageId) {
        return get("/compound/public/team?seasonId={seasonId}&stageIds={stageId}", seasonId, stageId);
    }

    public String fetchPlayerStatistics(long seasonId, long stageId) {
        return get("/compound/public/player?seasonId={seasonId}&stageIds={stageId}", seasonId, stageId);
    }

    public String fetchPlayerHeroRecords(long playerId, long seasonId, long stageId) {
        return get(
                "/compound/heroRecord?playerId={playerId}&seasonId={seasonId}&stageIds={stageId}",
                playerId, seasonId, stageId
        );
    }

    public String fetchMatchDetail(long matchId) {
        return get("/compound/matchDetail?matchId={matchId}", matchId);
    }

    private String get(String uri, Object... uriVariables) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String body = restClient.get().uri(uri, uriVariables).retrieve().body(String.class);
                if (body == null || body.isBlank()) {
                    throw new TjStatsSourceException("赛事官网接口返回空响应：" + uri);
                }
                return body;
            } catch (RestClientResponseException exception) {
                int statusCode = exception.getStatusCode().value();
                if (!isRetryableStatus(statusCode)) {
                    throw new TjStatsSourceException(
                            "赛事官网接口返回不可恢复的错误 HTTP " + statusCode + "：" + uri, exception);
                }
                // 可重试的 HTTP 错误（408, 425, 429, 5xx 等）继续重试
                lastFailure = exception;
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(attempt);
                }
            } catch (RestClientException exception) {
                // 连接/读取类异常，继续重试
                lastFailure = exception;
                if (attempt < MAX_ATTEMPTS) {
                    waitBeforeRetry(attempt);
                }
            }
        }
        throw new TjStatsSourceException("访问赛事官网接口失败，重试 " + MAX_ATTEMPTS + " 次后仍未恢复：" + uri, lastFailure);
    }

    private static boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500;
    }

    private static void waitBeforeRetry(int attempt) {
        try {
            Thread.sleep(250L * (1L << (attempt - 1)));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TjStatsSourceException("等待重试时任务被中断", exception);
        }
    }
}
