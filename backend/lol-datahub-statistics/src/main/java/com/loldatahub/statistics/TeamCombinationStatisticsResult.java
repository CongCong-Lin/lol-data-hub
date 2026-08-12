package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.TeamCombinationStatistics;
import com.loldatahub.domain.statistics.TeamCombinationType;

import java.util.List;

public record TeamCombinationStatisticsResult(
        long dataVersion,
        TeamCombinationType combinationType,
        int minimumPickCount,
        int total,
        List<TeamCombinationStatistics> items
) {
}
