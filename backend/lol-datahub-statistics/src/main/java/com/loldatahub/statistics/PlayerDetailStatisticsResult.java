package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.PlayerDetailProfile;
import com.loldatahub.domain.statistics.PlayerAverageContrastMetric;
import com.loldatahub.domain.statistics.PlayerHeroUsage;
import com.loldatahub.domain.statistics.PlayerRadarMetric;
import com.loldatahub.domain.statistics.RankedPlayerMetric;

import java.time.LocalDateTime;
import java.util.List;

public record PlayerDetailStatisticsResult(
        long dataVersion,
        int minimumMatchCount,
        String position,
        int cohortSize,
        PlayerDetailProfile player,
        List<RankedPlayerMetric> coreMetrics,
        List<PlayerRadarMetric> radarMetrics,
        boolean heroUsageAvailable,
        List<String> missingHeroStageKeys,
        long heroUsageTotalGames,
        List<PlayerHeroUsage> heroes,
        LocalDateTime latestCollectedAt,
        List<PlayerAverageContrastMetric> averageContrastMetrics
) {
    /** 保留旧构造签名，便于缓存反序列化和已有调用方平滑升级。 */
    public PlayerDetailStatisticsResult(
            long dataVersion,
            int minimumMatchCount,
            String position,
            int cohortSize,
            PlayerDetailProfile player,
            List<RankedPlayerMetric> coreMetrics,
            List<PlayerRadarMetric> radarMetrics,
            boolean heroUsageAvailable,
            List<String> missingHeroStageKeys,
            long heroUsageTotalGames,
            List<PlayerHeroUsage> heroes,
            LocalDateTime latestCollectedAt
    ) {
        this(dataVersion, minimumMatchCount, position, cohortSize, player, coreMetrics, radarMetrics,
                heroUsageAvailable, missingHeroStageKeys, heroUsageTotalGames, heroes, latestCollectedAt,
                List.of());
    }
}
