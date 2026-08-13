package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.util.List;

public record PlayerStatistics(
        String playerKey,
        Long sourcePlayerId,
        String playerName,
        String playerAvatar,
        List<String> teamNames,
        List<String> positions,
        long matchCount,
        long gameCount,
        long mvpCount,
        BigDecimal mvpVotes,
        long totalKills,
        long totalAssists,
        long totalDeaths,
        BigDecimal kda,
        BigDecimal killPerGame,
        BigDecimal assistPerGame,
        BigDecimal deathPerGame,
        BigDecimal goldPerGame,
        BigDecimal creepScorePerGame,
        BigDecimal wardPlacedPerGame,
        BigDecimal wardKilledPerGame,
        BigDecimal killParticipantPercent,
        BigDecimal goldGapPerGame,
        BigDecimal damagePerGame,
        BigDecimal damagePercent,
        BigDecimal goldPercent,
        boolean sampleQualified
) {
}
