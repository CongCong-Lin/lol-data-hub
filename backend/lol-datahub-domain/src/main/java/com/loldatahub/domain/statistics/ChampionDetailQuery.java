package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 英雄详情页查询条件。统计范围由 (sourceChampionId, stageKeys, minimumPickCount, position) 唯一确定，
 * position 为空表示查看英雄整体（不分路）。
 */
public record ChampionDetailQuery(
        long sourceChampionId,
        List<StageKey> stages,
        int minimumPickCount,
        String position
) {
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    public ChampionDetailQuery {
        if (sourceChampionId <= 0) {
            throw new IllegalArgumentException("英雄 ID 必须为正整数");
        }
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
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
        if (position != null && !position.isBlank()) {
            position = position.trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("TOP", "JUN", "MID", "BOT", "SUP").contains(position)) {
                throw new IllegalArgumentException("未知的英雄分路：" + position);
            }
        } else {
            position = null;
        }
    }

    public String cacheFingerprint() {
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        String pos = position == null ? "" : position;
        return sourceChampionId + ":" + stageKeysStr + ":" + minimumPickCount + ":" + pos;
    }
}
