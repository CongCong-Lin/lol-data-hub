package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PlayerStageStatWrite(
        long runId,
        long seasonId,
        long stageId,
        String playerKey,
        String teamName,
        String teamLogo,
        String playerPosition,
        long matchCount,
        long mvpCount,
        long mvpVotes,
        long totalKills,
        long totalAssists,
        long totalDeaths,
        BigDecimal sourceGoldPerGame,
        BigDecimal sourceCreepScorePerGame,
        BigDecimal sourceWardPlacedPerGame,
        BigDecimal sourceWardKilledPerGame,
        BigDecimal sourceKillParticipantPercent,
        BigDecimal sourceGoldGapPerGame,
        BigDecimal sourceDamagePercent,
        BigDecimal sourceGoldPercent,
        OffsetDateTime collectedAt
) {
}
