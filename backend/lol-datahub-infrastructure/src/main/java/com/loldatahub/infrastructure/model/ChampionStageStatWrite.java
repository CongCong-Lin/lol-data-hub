package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ChampionStageStatWrite(
        long runId,
        long seasonId,
        long stageId,
        long championId,
        long pickCount,
        long banCount,
        long bpCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists,
        BigDecimal sourcePickRate,
        BigDecimal sourceBanRate,
        BigDecimal sourceBpRate,
        BigDecimal sourceWinningRate,
        Long mostUsedPlayerId,
        String mostUsedPlayerName,
        OffsetDateTime collectedAt
) {
}

