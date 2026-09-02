package com.loldatahub.api;

import com.loldatahub.infrastructure.model.StageAvailabilityRow;

import java.time.OffsetDateTime;

public record StageView(
        long sourceSeasonId,
        long sourceStageId,
        String name,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean collected,
        Long sampleBaseCount,
        OffsetDateTime collectedAt
) {
    static StageView from(StageAvailabilityRow row) {
        return from(row, row.name());
    }

    static StageView from(StageAvailabilityRow row, String displayName) {
        return new StageView(
                row.sourceSeasonId(), row.sourceStageId(), displayName, row.startTime(), row.endTime(),
                row.collected(), row.sampleBaseCount(), row.collectedAt()
        );
    }
}
