package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 选手英雄使用统计：按选手、位置、复合赛段在逐局英雄明细上跨赛段求和后重新计算派生指标。
 * 选取率分母为该选手英雄明细全部 pickCount 之和，分子分母来自同一张事实表。
 */
public record PlayerHeroUsage(
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championTitle,
        String championLogo,
        long pickCount,
        BigDecimal pickRate,
        long winningCount,
        BigDecimal winningRate,
        long totalKills,
        long totalDeaths,
        long totalAssists,
        BigDecimal kda,
        BigDecimal killPerGame,
        BigDecimal deathPerGame,
        BigDecimal assistPerGame
) {
}
