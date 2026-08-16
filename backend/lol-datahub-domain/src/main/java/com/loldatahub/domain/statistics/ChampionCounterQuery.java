package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 英雄对位克制查询：统计指定英雄在某个分路上与对手英雄的对位胜负。
 */
public record ChampionCounterQuery(
        List<StageKey> stages,
        long championId,
        String position,
        int minimumGames
) {
    private static final Set<String> ALLOWED_POSITIONS = Set.of("TOP", "JUN", "MID", "BOT", "SUP");
    private static final int MAX_STAGES = 50;
    private static final int MAX_THRESHOLD = 10_000;

    public ChampionCounterQuery {
        stages = stages == null ? List.of() : stages.stream().distinct().sorted().toList();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        if (stages.size() > MAX_STAGES) {
            throw new IllegalArgumentException("跨赛事查询最多支持 " + MAX_STAGES + " 个赛段，当前 " + stages.size() + " 个");
        }
        if (championId <= 0) {
            throw new IllegalArgumentException("无效的英雄 ID");
        }
        position = normalizePosition(position);
        if (minimumGames < 0 || minimumGames > MAX_THRESHOLD) {
            throw new IllegalArgumentException("最低对位场次数必须在 0 到 " + MAX_THRESHOLD + " 之间");
        }
    }

    private static String normalizePosition(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("对位克制查询必须指定分路");
        }
        String upper = value.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_POSITIONS.contains(upper)) {
            throw new IllegalArgumentException("未知的分路：" + value);
        }
        return upper;
    }

    public String cacheFingerprint() {
        String stageKeys = stages.stream().map(StageKey::canonical).reduce((a, b) -> a + "," + b).orElse("");
        return stageKeys + ":" + championId + ":" + position + ":" + minimumGames;
    }
}
