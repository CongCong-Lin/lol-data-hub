package com.loldatahub.infrastructure.model;

/**
 * 英雄分路统计读取行：英雄在某个分路的实际使用合计，来自逐局英雄明细表。
 */
public record ChampionPositionStatRow(
        String position,
        long pickCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists
) {
}
