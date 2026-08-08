package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ChampionStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumPickCount,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "pickCount", "winningRate", "pickRate", "banRate", "championName", "bpRate"
    );
    private static final String DEFAULT_SORT_BY = "bpRate";

    public ChampionStatisticsQuery {
        stageIds = stageIds == null ? List.of() : stageIds.stream().distinct().sorted().toList();
        if (minimumPickCount < 0) {
            throw new IllegalArgumentException("最低出场次数不能小于 0");
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
        return seasonId + ":" + stageIds + ":" + minimumPickCount + ":"
                + sortBy + ":" + sortDirection.name();
    }

}
