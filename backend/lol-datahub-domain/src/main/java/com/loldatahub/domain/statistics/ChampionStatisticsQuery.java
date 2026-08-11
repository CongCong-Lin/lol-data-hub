package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record ChampionStatisticsQuery(
        List<StageKey> stages,
        int minimumPickCount,
        String position,
        String sortBy,
        SortDirection sortDirection
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "championName", "positions", "pickCount", "pickRate", "banCount", "banRate", "bpRate",
            "winningCount", "winningRate", "totalKills", "killPerGame", "totalAssists",
            "assistPerGame", "totalDeaths", "deathPerGame", "kda", "mostUsedPlayers"
    );
    private static final String DEFAULT_SORT_BY = "bpRate";
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;
    private static final Set<String> ALLOWED_POSITIONS = Set.of("TOP", "JUN", "MID", "BOT", "SUP");

    /**
     * 旧参数兼容构造器：从 (seasonId, stageIds, ...) 转换为 canonical 形式。
     */
    public ChampionStatisticsQuery(long seasonId, List<Long> stageIds, int minimumPickCount,
                                   String sortBy, SortDirection sortDirection) {
        this(StageKey.fromSeasonStages(seasonId, stageIds), minimumPickCount, null, sortBy, sortDirection);
    }

    public ChampionStatisticsQuery(long seasonId, List<Long> stageIds, int minimumPickCount,
                                   String position, String sortBy, SortDirection sortDirection) {
        this(StageKey.fromSeasonStages(seasonId, stageIds), minimumPickCount, position, sortBy, sortDirection);
    }

    public ChampionStatisticsQuery(List<StageKey> stages, int minimumPickCount,
                                   String sortBy, SortDirection sortDirection) {
        this(stages, minimumPickCount, null, sortBy, sortDirection);
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
        position = normalizePosition(position);
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = DEFAULT_SORT_BY;
        } else {
            sortBy = normalizeSortBy(sortBy);
        }
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
    }

    private static String normalizePosition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_POSITIONS.contains(normalized)) {
            throw new IllegalArgumentException("未知的英雄分路：" + value);
        }
        return normalized;
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
        String pos = position == null ? "" : position;
        return stageKeysStr + ":" + minimumPickCount + ":" + pos + ":"
                + sortBy + ":" + sortDirection.name();
    }
}
