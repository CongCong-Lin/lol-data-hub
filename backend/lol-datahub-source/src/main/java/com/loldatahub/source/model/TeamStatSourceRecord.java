package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TeamStatSourceRecord(
        long teamId,
        String teamName,
        String teamLogo,
        long matchCount,
        long gameCount,
        long matchWinCount,
        BigDecimal winningRate,
        long totalKills,
        BigDecimal killPerGameTeam,
        long totalDeath,
        BigDecimal deathPerGameTeam,
        BigDecimal wardPlacedPerGameTeam,
        BigDecimal wardKilledPerGameTeam,
        BigDecimal goldPerGameTeam,
        BigDecimal baronKillPerGameTeam,
        BigDecimal drakeKillPerGameTeam
) {
}
