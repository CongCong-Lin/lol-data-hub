package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 战队 Elo 评分查询：按开赛时间顺序重放所选赛段的全部小局，无状态计算当前评分。
 */
public record EloRatingQuery(
        List<StageKey> stages
) {
    private static final int MAX_STAGES = 50;

    public EloRatingQuery {
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
    }

    public String cacheFingerprint() {
        return stages.stream().map(StageKey::canonical).reduce((a, b) -> a + "," + b).orElse("");
    }
}
