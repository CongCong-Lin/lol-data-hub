package com.loldatahub.domain.statistics;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public record ChampionStatisticsQuery(
        long seasonId,
        List<Long> stageIds,
        int minimumPickCount,
        String sortBy,
        SortDirection sortDirection
) {
    public ChampionStatisticsQuery {
        stageIds = stageIds == null ? List.of() : stageIds.stream().distinct().sorted().toList();
        minimumPickCount = Math.max(0, minimumPickCount);
        sortBy = sortBy == null || sortBy.isBlank() ? "bpRate" : sortBy;
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
    }

    public String cacheFingerprint() {
        return seasonId + ":" + stageIds + ":" + minimumPickCount + ":"
                + sortBy.toLowerCase(Locale.ROOT) + ":" + sortDirection.name();
    }

    public enum SortDirection {
        ASC,
        DESC;

        public static SortDirection from(String value) {
            return "asc".equalsIgnoreCase(value) ? ASC : DESC;
        }

        public <T> Comparator<T> apply(Comparator<T> comparator) {
            return this == DESC ? comparator.reversed() : comparator;
        }
    }
}

