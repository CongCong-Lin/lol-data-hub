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
        long mvpCount,
        long mvpVotes,
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
        BigDecimal damagePercent,
        BigDecimal goldPercent,
        boolean sampleQualified
) {
}
