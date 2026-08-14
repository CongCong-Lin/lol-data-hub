package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

public record TeamStatistics(
        long teamId,
        String teamName,
        String teamLogo,
        long matchCount,
        long gameCount,
        long matchWinCount,
        BigDecimal winningRate,
        BigDecimal kda,
        long totalKills,
        BigDecimal killPerGame,
        long totalDeaths,
        BigDecimal deathPerGame,
        BigDecimal damagePerGame,
        BigDecimal averageGameDurationSeconds,
        BigDecimal goldPerMinute,
        BigDecimal wardPlacedPerMinute,
        BigDecimal wardKilledPerMinute,
        BigDecimal drakeControlRate,
        BigDecimal baronControlRate,
        BigDecimal firstBloodRate,
        BigDecimal damagePerMinute,
        BigDecimal creepScorePerMinute,
        BigDecimal turretKillPerGame,
        BigDecimal turretLostPerGame,
        BigDecimal wardPlacedPerGame,
        BigDecimal wardKilledPerGame,
        BigDecimal goldPerGame,
        BigDecimal baronKillPerGame,
        BigDecimal drakeKillPerGame,
        boolean sampleQualified
) {
}
