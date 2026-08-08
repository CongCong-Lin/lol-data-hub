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
        long mvpCount,
        long mvpVotes,
        long totalKills,
        long totalAssists,
        long totalDeaths,
        BigDecimal weightedGoldPerGame,
        BigDecimal weightedCreepScorePerGame,
        BigDecimal weightedWardPlacedPerGame,
        BigDecimal weightedWardKilledPerGame,
        BigDecimal weightedKillParticipantPercent,
        BigDecimal weightedGoldGapPerGame,
        BigDecimal weightedDamagePercent,
        BigDecimal weightedGoldPercent
) {
}
