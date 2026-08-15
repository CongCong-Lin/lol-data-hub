package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 对局赛果列表查询条件。按所选赛段返回逐局对局记录，支持时间/比赛 ID 排序与分页。
 */
public record MatchGameQuery(
        List<StageKey> stages,
        String sortBy,
        SortDirection sortDirection,
        int offset,
        int limit
) {
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("startTime", "matchId");
    private static final String DEFAULT_SORT_BY = "startTime";
    private static final int MAX_STAGES = 50;
    private static final int MAX_OFFSET = 100_000;
    private static final int MAX_LIMIT = 500;

    public MatchGameQuery {
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
        if (sortBy == null || sortBy.isBlank()) {
            sortBy = DEFAULT_SORT_BY;
        } else {
            String lower = sortBy.trim().toLowerCase(Locale.ROOT);
            for (String allowed : ALLOWED_SORT_FIELDS) {
                if (allowed.equals(lower)) {
                    sortBy = allowed;
                    break;
                }
            }
            if (!ALLOWED_SORT_FIELDS.contains(sortBy)) {
                throw new IllegalArgumentException("不支持的排序字段：" + sortBy);
            }
        }
        sortDirection = sortDirection == null ? SortDirection.DESC : sortDirection;
        if (offset < 0) {
            throw new IllegalArgumentException("分页偏移量不能小于 0");
        }
        if (offset > MAX_OFFSET) {
            throw new IllegalArgumentException("分页偏移量不能超过 " + MAX_OFFSET);
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("每页数量必须是 1 到 " + MAX_LIMIT + " 之间的整数");
        }
    }

    public String cacheFingerprint() {
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return stageKeysStr + ":" + sortBy + ":" + sortDirection.name() + ":" + offset + ":" + limit;
    }
}
