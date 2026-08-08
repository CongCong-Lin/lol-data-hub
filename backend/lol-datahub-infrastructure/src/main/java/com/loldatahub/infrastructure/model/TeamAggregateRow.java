package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;

public record TeamAggregateRow(
        long teamId,
        String teamName,
        String teamLogo,
        long matchCount,
        long matchWinCount,
        long totalKills,
        long totalDeaths,
        BigDecimal weightedWardPlacedPerGame,
        BigDecimal weightedWardKilledPerGame,
        BigDecimal weightedGoldPerGame,
        BigDecimal weightedBaronKillPerGame,
        BigDecimal weightedDrakeKillPerGame
) {
}
