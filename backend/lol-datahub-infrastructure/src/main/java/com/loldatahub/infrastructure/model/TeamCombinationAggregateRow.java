package com.loldatahub.infrastructure.model;

public record TeamCombinationAggregateRow(
        long teamId,
        String teamName,
        String teamLogo,
        long firstChampionId,
        String firstChampionName,
        String firstChampionTitle,
        String firstChampionLogo,
        long secondChampionId,
        String secondChampionName,
        String secondChampionTitle,
        String secondChampionLogo,
        long pickCount,
        long validGameCount,
        long winningCount
) {
}
