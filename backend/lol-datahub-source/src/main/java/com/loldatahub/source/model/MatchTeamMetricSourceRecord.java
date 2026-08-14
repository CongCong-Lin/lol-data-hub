package com.loldatahub.source.model;

import java.math.BigDecimal;

/** 官网单局详情中一支战队的可累加指标。gameTimeSeconds 为该局实际时长（秒）。 */
public record MatchTeamMetricSourceRecord(
        long matchId,
        long bo,
        long teamId,
        long gameTimeSeconds,
        long totalAssists,
        BigDecimal heroDamage,
        BigDecimal gold,
        long wardPlaced,
        long wardKilled,
        long minionKills,
        long dragonAmount,
        long baronAmount,
        long turretAmount,
        boolean firstBlood
) {
}
