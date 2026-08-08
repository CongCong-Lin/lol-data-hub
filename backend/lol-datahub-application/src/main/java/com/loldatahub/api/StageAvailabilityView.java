package com.loldatahub.api;

import com.loldatahub.infrastructure.model.CrossSeasonStageAvailabilityRow;

import java.time.OffsetDateTime;

/**
 * 跨赛事赛段可用性视图，包含赛季名称。
 */
public record StageAvailabilityView(
        long sourceSeasonId,
        long sourceStageId,
        String seasonName,
        String name,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean collected,
        Long sampleBaseCount,
        OffsetDateTime collectedAt
) {
    static StageAvailabilityView from(CrossSeasonStageAvailabilityRow row) {
        return new StageAvailabilityView(
                row.sourceSeasonId(), row.sourceStageId(), row.seasonName(),
                row.name(), row.startTime(), row.endTime(),
                row.collected(), row.sampleBaseCount(), row.collectedAt()
        );
    }
}
