package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

public record TeamCombinationStatistics(
        long teamId,
        String teamName,
        String teamLogo,
        TeamCombinationType combinationType,
        String firstPosition,
        long firstChampionId,
        String firstChampionName,
        String firstChampionTitle,
        String firstChampionLogo,
        String secondPosition,
        long secondChampionId,
        String secondChampionName,
        String secondChampionTitle,
        String secondChampionLogo,
        long pickCount,
        long validGameCount,
        BigDecimal pickRate,
        long winningCount,
        BigDecimal winningRate,
        boolean sampleQualified
) {
}
