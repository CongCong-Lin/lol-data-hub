package com.loldatahub.source;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TjStatsClient {
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

    private String get(String uri, Object... uriVariables) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String body = restClient.get().uri(uri, uriVariables).retrieve().body(String.class);
                if (body == null || body.isBlank()) {
                    throw new TjStatsSourceException("赛事官网接口返回空响应：" + uri);
                }
                return body;
            } catch (RestClientException exception) {
                lastFailure = exception;
                if (attempt < 3) {
                    waitBeforeRetry(attempt);
                }
            }
        }
        throw new TjStatsSourceException("访问赛事官网接口失败，重试 3 次后仍未恢复：" + uri, lastFailure);
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
