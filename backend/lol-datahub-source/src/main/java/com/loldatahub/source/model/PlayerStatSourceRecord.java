package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlayerStatSourceRecord(
        Long playerId,
        Long teamId,
        String playerAvatar,
        String playerName,
        String playerLocation,
        String teamName,
        String teamLogo,
        long matchCount,
        long mvpCount,
        long mvpVotes,
        BigDecimal kda,
        long totalKills,
        long totalAssists,
        long totalDeath,
        BigDecimal goldPerGame,
        BigDecimal creepScorePerGame,
        BigDecimal wardPlacedPerGame,
        BigDecimal wardKilledPerGame,
        BigDecimal killParticipantPercent,
        BigDecimal goldGapPerGame,
        BigDecimal damagePercent,
        BigDecimal goldPercent
) {
}
