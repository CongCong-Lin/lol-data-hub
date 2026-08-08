package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record ChampionStatistics(
        long championId,
        String championName,
        String championTitle,
        String championLogo,
        List<String> positions,
        long sampleBaseCount,
        long pickCount,
        long banCount,
        long bpCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists,
        BigDecimal pickRate,
        BigDecimal banRate,
        BigDecimal bpRate,
        BigDecimal winningRate,
        BigDecimal kda,
        BigDecimal killPerGame,
        BigDecimal assistPerGame,
        BigDecimal deathPerGame,
        List<String> mostUsedPlayers,
        boolean sampleQualified,
        OffsetDateTime sourceUpdatedAt
) {
}

