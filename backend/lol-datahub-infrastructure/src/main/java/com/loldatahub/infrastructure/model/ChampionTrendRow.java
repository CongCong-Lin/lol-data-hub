package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;

/**
 * 英雄按赛段的指标趋势读取行，字段与官网赛段统计口径一致。
 */
public record ChampionTrendRow(
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
