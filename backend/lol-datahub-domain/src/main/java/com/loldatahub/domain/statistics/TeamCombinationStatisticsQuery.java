package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record TeamCombinationStatisticsQuery(
        List<StageKey> stages,
        TeamCombinationType combinationType,
        int minimumPickCount,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "teamName", "firstChampionName", "secondChampionName", "pickCount",
            "validGameCount", "pickRate", "winningCount", "winningRate"
    );
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    public TeamCombinationStatisticsQuery {
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        combinationType = combinationType == null ? TeamCombinationType.MID_JUNGLE : combinationType;
        if (minimumPickCount < 0 || minimumPickCount > MAX_THRESHOLD) {
            throw new IllegalArgumentException("最低组合选取次数必须在 0 到 " + MAX_THRESHOLD + " 之间");
        }
        sortBy = sortBy == null || sortBy.isBlank() ? "pickCount" : normalizeSortBy(sortBy);
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
    }

    private static String normalizeSortBy(String value) {
        String lower = value.trim().toLowerCase(Locale.ROOT);
        return ALLOWED_SORT_FIELDS.stream()
                .filter(field -> field.toLowerCase(Locale.ROOT).equals(lower))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的排序字段：" + value));
    }

    public String cacheFingerprint() {
        String stageKeys = stages.stream().map(StageKey::canonical).reduce((a, b) -> a + "," + b).orElse("");
        return stageKeys + ":" + combinationType.name() + ":" + minimumPickCount
                + ":" + sortBy + ":" + sortDirection.name();
    }
}
