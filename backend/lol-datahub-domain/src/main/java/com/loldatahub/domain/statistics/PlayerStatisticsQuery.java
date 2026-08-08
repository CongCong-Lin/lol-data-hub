package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record PlayerStatisticsQuery(
        List<StageKey> stages,
        int minimumMatchCount,
        String position,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "totalKills", "mvpCount", "killPerGame", "goldPerGame", "damagePercent", "matchCount", "kda"
    );
    private static final String DEFAULT_SORT_BY = "kda";
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    /**
     * 旧参数兼容构造器：从 (seasonId, stageIds, ...) 转换为 canonical 形式。
     */
    public PlayerStatisticsQuery(long seasonId, List<Long> stageIds, int minimumMatchCount,
                                 String position, String sortBy, SortDirection sortDirection) {
        this(StageKey.fromSeasonStages(seasonId, stageIds), minimumMatchCount, position, sortBy, sortDirection);
    }

    public PlayerStatisticsQuery {
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
        if (minimumMatchCount < 0) {
            throw new IllegalArgumentException("最低比赛场数不能小于 0");
        }
        if (minimumMatchCount > MAX_THRESHOLD) {
            throw new IllegalArgumentException("最低比赛场数不能超过 " + MAX_THRESHOLD);
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
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String pos = position == null ? "" : position;
        return stageKeysStr + ":" + minimumMatchCount + ":"
                + pos + ":" + sortBy + ":" + sortDirection.name();
    }
}
