package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 选手详情页查询条件。详情页统计范围由
 * (sourcePlayerId, stageKeys, position, minimumMatchCount) 唯一确定，
 * 与产生选手统计列表的查询条件保持一致。
 */
public record PlayerDetailQuery(
        long sourcePlayerId,
        List<StageKey> stages,
        String position,
        int minimumMatchCount
) {
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10000;

    public PlayerDetailQuery {
        if (sourcePlayerId <= 0) {
            throw new IllegalArgumentException("选手 ID 必须为正整数");
        }
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        if (position == null || position.isBlank()) {
            throw new IllegalArgumentException("选手详情查询必须指定位置");
        }
        position = PlayerStatisticsMath.validatePosition(position);
        if (minimumMatchCount < 0) {
            throw new IllegalArgumentException("最低比赛场数不能小于 0");
        }
        if (minimumMatchCount > MAX_THRESHOLD) {
            throw new IllegalArgumentException("最低比赛场数不能超过 " + MAX_THRESHOLD);
        }
    }

    /**
     * 逐局英雄明细表使用的位置代码（JUG→JUN，AD→BOT）。
     */
    public String heroPosition() {
        return PlayerStatisticsMath.toHeroPosition(position);
    }

    /**
     * 规范化缓存指纹：sourcePlayerId:canonical(排序去重后的赛段键):position:minimumMatchCount。
     * 参数顺序不同但内容相同时指纹一致。
     */
    public String cacheFingerprint() {
        String stageKeysStr = stages.stream()
                .map(StageKey::canonical)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return sourcePlayerId + ":" + stageKeysStr + ":" + position + ":" + minimumMatchCount;
    }
}
