package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.PlayerDetailProfile;
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
        LocalDateTime latestCollectedAt
) {
}
