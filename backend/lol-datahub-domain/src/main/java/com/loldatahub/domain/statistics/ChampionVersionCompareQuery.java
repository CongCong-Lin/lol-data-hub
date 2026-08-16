package com.loldatahub.domain.statistics;

import java.time.LocalDate;
import java.util.List;

/**
 * 版本窗口对比查询：用英雄统计快照分别还原两个日期时点的累计数据并对比差异。
 * 每个窗口取各赛段内 collected_at 不晚于窗口日期的最近一次快照；早于全部快照的窗口回退到最早快照。
 */
public record ChampionVersionCompareQuery(
        List<StageKey> stages,
        LocalDate fromDate,
        LocalDate toDate
) {
    private static final int MAX_STAGES = 50;

    public ChampionVersionCompareQuery {
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("版本对比需要同时指定起始与结束日期");
        }
        if (!fromDate.isBefore(toDate)) {
            throw new IllegalArgumentException("起始日期必须早于结束日期");
        }
    }

    public String cacheFingerprint() {
        String stageKeys = stages.stream().map(StageKey::canonical).reduce((a, b) -> a + "," + b).orElse("");
        return stageKeys + ":" + fromDate + ":" + toDate;
    }
}
