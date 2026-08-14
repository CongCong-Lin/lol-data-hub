package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;

public record TeamAggregateRow(
        long teamId,
        String teamName,
        String teamLogo,
        long matchCount,
        long gameCount,
        long matchWinCount,
        long totalKills,
        long totalDeaths,
        Long totalAssists,
        BigDecimal totalDamage,
        BigDecimal weightedWardPlacedPerGame,
        BigDecimal weightedWardKilledPerGame,
        BigDecimal weightedGoldPerGame,
        BigDecimal weightedBaronKillPerGame,
        BigDecimal weightedDrakeKillPerGame
) {
}
