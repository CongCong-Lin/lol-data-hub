package com.loldatahub.infrastructure.model;

import java.time.OffsetDateTime;

public record StageAvailabilityRow(
        long sourceSeasonId,
        long sourceStageId,
        String name,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean collected,
        Long sampleBaseCount,
        OffsetDateTime collectedAt
) {
}
