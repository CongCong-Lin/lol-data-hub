package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.PlayerStatistics;

import java.util.List;

public record PlayerStatisticsResult(
        long dataVersion,
        int minimumMatchCount,
        int total,
        List<PlayerStatistics> items
) {
}
