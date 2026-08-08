package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.TeamStatistics;

import java.util.List;

public record TeamStatisticsResult(
        long dataVersion,
        int minimumMatchCount,
        int total,
        List<TeamStatistics> items
) {
}
