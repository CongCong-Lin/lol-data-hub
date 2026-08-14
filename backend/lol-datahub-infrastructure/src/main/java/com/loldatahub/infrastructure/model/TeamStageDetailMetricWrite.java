package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TeamStageDetailMetricWrite(
        long runId,
        long seasonId,
        long stageId,
        long teamId,
        long gameCount,
        long totalAssists,
        BigDecimal totalDamage,
        Long totalGameSeconds,
        BigDecimal totalGold,
        Long totalWardsPlaced,
        Long totalWardsKilled,
        Long totalMinionKills,
        Long totalDragons,
        Long totalDragonOpportunities,
        Long totalBarons,
        Long totalBaronOpportunities,
        Long totalTurrets,
        Long totalTurretsLost,
        Long firstBloodGames,
        OffsetDateTime collectedAt
) {
}
