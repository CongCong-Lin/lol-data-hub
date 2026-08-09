package com.loldatahub.infrastructure.model;

import java.time.OffsetDateTime;

public record ChampionPositionPlayerStageStatWrite(
        long runId,
        long seasonId,
        long stageId,
        long championId,
        String position,
        long playerId,
        String playerName,
        long pickCount,
        long winningCount,
        long totalKills,
        long totalDeaths,
        long totalAssists,
        OffsetDateTime collectedAt
) {
}
