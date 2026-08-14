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
        OffsetDateTime collectedAt
) {
}
