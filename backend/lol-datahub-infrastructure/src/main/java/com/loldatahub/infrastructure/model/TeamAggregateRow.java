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
        Long totalGameSeconds,
        BigDecimal totalGold,
        Long totalWardsPlaced,
        Long totalWardsKilled,
        Long totalMinionKills,
        Long totalDragons,
        Long totalDragonOpportunities,
        Long totalBarons,
        Long totalBaronOpportunities,
        Long totalTurrets,
        Long totalTurretsLost,
        Long firstBloodGames,
        BigDecimal weightedWardPlacedPerGame,
        BigDecimal weightedWardKilledPerGame,
        BigDecimal weightedGoldPerGame,
        BigDecimal weightedBaronKillPerGame,
        BigDecimal weightedDrakeKillPerGame
) {
}
