package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;

public record TeamStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumMatchCount,
        String sortBy,
        SortDirection sortDirection
) {
    public TeamStatisticsQuery {
        stageIds = stageIds == null ? List.of() : stageIds.stream().distinct().sorted().toList();
        if (minimumMatchCount < 0) {
            throw new IllegalArgumentException("最低比赛场数不能小于 0");
        }
        sortBy = sortBy == null || sortBy.isBlank() ? "winningRate" : sortBy;
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
    }

    public String cacheFingerprint() {
        return seasonId + ":" + stageIds + ":" + minimumMatchCount + ":"
                + sortBy.toLowerCase(Locale.ROOT) + ":" + sortDirection.name();
    }
}
