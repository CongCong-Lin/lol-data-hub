package com.loldatahub.infrastructure.model;

public record PlayerHeroUsageAggregateRow(
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championTitle,
        String championLogo,
        long pickCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists
) {
}
