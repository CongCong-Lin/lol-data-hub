package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 英雄在某个分路的实际使用统计，来自逐局英雄明细表。
 */
public record ChampionPositionStat(
        String position,
        long pickCount,
        long winningCount,
        BigDecimal pickRate,
        BigDecimal winningRate,
        BigDecimal kda
) {
}
