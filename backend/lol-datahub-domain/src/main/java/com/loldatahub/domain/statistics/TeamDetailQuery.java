package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 战队详情页查询条件。统计范围由 (sourceTeamId, stageKeys, minimumMatchCount) 唯一确定。
 */
public record TeamDetailQuery(
        long sourceTeamId,
        List<StageKey> stages,
        int minimumMatchCount
) {
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    public TeamDetailQuery {
        if (sourceTeamId <= 0) {
            throw new IllegalArgumentException("战队 ID 必须为正整数");
        }
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
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
    }

    public String cacheFingerprint() {
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return sourceTeamId + ":" + stageKeysStr + ":" + minimumMatchCount;
    }
}
