package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ChampionStatisticsQuery(
        List<StageKey> stages,
        int minimumPickCount,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "pickCount", "winningRate", "pickRate", "banRate", "championName", "bpRate"
    );
    private static final String DEFAULT_SORT_BY = "bpRate";
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    /**
     * 旧参数兼容构造器：从 (seasonId, stageIds, ...) 转换为 canonical 形式。
     */
    public ChampionStatisticsQuery(long seasonId, List<Long> stageIds, int minimumPickCount,
                                   String sortBy, SortDirection sortDirection) {
        this(StageKey.fromSeasonStages(seasonId, stageIds), minimumPickCount, sortBy, sortDirection);
    }

    public ChampionStatisticsQuery {
        if (stages == null) {
            stages = List.of();
        }
        stages = stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        if (minimumPickCount < 0) {
            throw new IllegalArgumentException("最低出场次数不能小于 0");
        }
        if (minimumPickCount > MAX_THRESHOLD) {
            throw new IllegalArgumentException("最低出场次数不能超过 " + MAX_THRESHOLD);
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

    /**
     * 缓存指纹：使用规范化 StageKey canonical 列表。
     * 跨赛事且参数顺序不同必须产生相同指纹。
     */
    public String cacheFingerprint() {
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return stageKeysStr + ":" + minimumPickCount + ":"
                + sortBy + ":" + sortDirection.name();
    }
}
