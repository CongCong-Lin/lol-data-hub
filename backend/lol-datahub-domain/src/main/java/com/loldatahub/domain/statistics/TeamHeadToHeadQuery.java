package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 战队交锋记录查询：统计一支战队在所选赛段内与每个对手的系列赛与小局胜负。
 */
public record TeamHeadToHeadQuery(
        List<StageKey> stages,
        long teamId
) {
    private static final int MAX_STAGES = 50;

    public TeamHeadToHeadQuery {
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        if (teamId <= 0) {
            throw new IllegalArgumentException("无效的战队 ID");
        }
    }

    public String cacheFingerprint() {
        String stageKeys = stages.stream().map(StageKey::canonical).reduce((a, b) -> a + "," + b).orElse("");
        return stageKeys + ":" + teamId;
    }
}
