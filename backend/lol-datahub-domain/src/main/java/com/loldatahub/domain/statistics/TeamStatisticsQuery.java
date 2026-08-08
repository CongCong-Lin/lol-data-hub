package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record TeamStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumMatchCount,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "totalKills", "killPerGame", "matchCount", "baronKillPerGame", "winningRate"
    );
    private static final String DEFAULT_SORT_BY = "winningRate";

    public TeamStatisticsQuery {
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
        return seasonId + ":" + stageIds + ":" + minimumMatchCount + ":"
                + sortBy + ":" + sortDirection.name();
    }
}
