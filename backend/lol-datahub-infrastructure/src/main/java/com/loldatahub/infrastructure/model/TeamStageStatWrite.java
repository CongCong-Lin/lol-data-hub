package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TeamStageStatWrite(
        long runId,
        long seasonId,
        long stageId,
        long teamId,
        long matchCount,
        long gameCount,
        long matchWinCount,
        long totalKills,
        long totalDeaths,
        BigDecimal sourceWardPlacedPerGame,
        BigDecimal sourceWardKilledPerGame,
        BigDecimal sourceGoldPerGame,
        BigDecimal sourceBaronKillPerGame,
        BigDecimal sourceDrakeKillPerGame,
        OffsetDateTime collectedAt
) {
}
