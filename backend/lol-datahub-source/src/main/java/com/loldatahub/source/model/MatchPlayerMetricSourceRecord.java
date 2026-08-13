package com.loldatahub.source.model;

import java.math.BigDecimal;

/**
 * 比赛详情中的单局选手指标。比例由逐局分子/分母重算，避免使用官网聚合接口已经取整的值。
 */
public record MatchPlayerMetricSourceRecord(
        long matchId,
        long bo,
        long playerId,
        long teamId,
        long kills,
        long assists,
        long deaths,
        long teamKills,
        BigDecimal heroDamage,
        BigDecimal teamHeroDamage,
        BigDecimal playerGold,
        BigDecimal teamGold,
        BigDecimal killParticipantPercent,
        BigDecimal damagePercent,
        BigDecimal goldPercent
) {
}
