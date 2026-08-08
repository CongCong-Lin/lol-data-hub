package com.loldatahub.domain.statistics;

import java.util.List;

public record PlayerStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumMatchCount,
        String position,
        String sortBy,
        SortDirection sortDirection
) {
    public PlayerStatisticsQuery {
        stageIds = stageIds == null ? List.of() : stageIds.stream().distinct().sorted().toList();
        if (minimumMatchCount < 0) {
            throw new IllegalArgumentException("最低比赛场数不能小于 0");
        }
        sortBy = sortBy == null || sortBy.isBlank() ? "kda" : sortBy;
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
        position = PlayerStatisticsMath.validatePosition(position);
    }

    public String cacheFingerprint() {
        String pos = position == null ? "" : position;
        return seasonId + ":" + stageIds + ":" + minimumMatchCount + ":"
                + pos + ":" + sortBy.toLowerCase(java.util.Locale.ROOT) + ":" + sortDirection.name();
    }
}
