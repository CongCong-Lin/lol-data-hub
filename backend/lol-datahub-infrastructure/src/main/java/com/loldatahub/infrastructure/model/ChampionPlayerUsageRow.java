package com.loldatahub.infrastructure.model;

/**
 * 英雄常用选手榜读取行：某个分路上使用该英雄次数达到门槛的选手合计。
 */
public record ChampionPlayerUsageRow(
        long sourcePlayerId,
        String playerName,
        String playerAvatar,
        String position,
        long pickCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists
) {
}
