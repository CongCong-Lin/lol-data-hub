package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;

public record PlayerAggregateRow(
        String playerKey,
        Long sourcePlayerId,
        String playerName,
        String avatarUrl,
        String teamNamesCsv,
        String positionsCsv,
        long matchCount,
        long gameCount,
        long mvpCount,
        BigDecimal mvpVotes,
        long totalKills,
        long totalAssists,
        long totalDeaths,
        BigDecimal weightedGoldPerGame,
        BigDecimal weightedCreepScorePerGame,
        BigDecimal weightedWardPlacedPerGame,
        BigDecimal weightedWardKilledPerGame,
        BigDecimal weightedKillParticipantPercent,
        BigDecimal weightedGoldGapPerGame,
        BigDecimal weightedDamagePerGame,
        BigDecimal weightedDamagePercent,
        BigDecimal weightedGoldPercent
) {
}
