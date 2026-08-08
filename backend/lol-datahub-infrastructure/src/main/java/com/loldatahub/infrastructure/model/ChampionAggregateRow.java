package com.loldatahub.infrastructure.model;

import java.time.OffsetDateTime;

public record ChampionAggregateRow(
        long championId,
        String championName,
        String championTitle,
        String championLogo,
        String positionsCsv,
        String mostUsedPlayersCsv,
        long sampleBaseCount,
        long pickCount,
        long banCount,
        long bpCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists,
        OffsetDateTime sourceUpdatedAt
) {
}

