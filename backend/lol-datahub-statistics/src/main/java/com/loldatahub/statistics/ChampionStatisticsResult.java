package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.ChampionStatistics;

import java.util.List;

public record ChampionStatisticsResult(
        long dataVersion,
        int minimumPickCount,
        int total,
        List<ChampionStatistics> items
) {
}

