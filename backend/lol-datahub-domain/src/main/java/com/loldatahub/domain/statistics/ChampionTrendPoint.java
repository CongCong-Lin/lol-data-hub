package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 英雄按赛段的指标趋势点，用于详情页趋势图。
 */
public record ChampionTrendPoint(
        long sourceSeasonId,
        long sourceStageId,
        String stageName,
        long pickCount,
        long banCount,
        long winningCount,
        BigDecimal pickRate,
        BigDecimal banRate,
        BigDecimal winningRate
) {
}
