package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PlayerStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumMatchCount,
        String position,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "totalKills", "mvpCount", "killPerGame", "goldPerGame", "damagePercent", "matchCount", "kda"
    );
    private static final String DEFAULT_SORT_BY = "kda";

    public PlayerStatisticsQuery {
        stageIds = stageIds == null ? List.of() : stageIds.stream().distinct().sorted().toList();
        if (minimumMatchCount < 0) {
            throw new IllegalArgumentException("最低比赛场数不能小于 0");
        }
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = DEFAULT_SORT_BY;
        } else {
            sortBy = normalizeSortBy(sortBy);
        }
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
        position = PlayerStatisticsMath.validatePosition(position);
    }

    private static String normalizeSortBy(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        for (String allowed : ALLOWED_SORT_FIELDS) {
            if (allowed.toLowerCase(Locale.ROOT).equals(lower)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException("不支持的排序字段：" + value);
    }

    public String cacheFingerprint() {
        String pos = position == null ? "" : position;
        return seasonId + ":" + stageIds + ":" + minimumMatchCount + ":"
                + pos + ":" + sortBy + ":" + sortDirection.name();
    }
}
