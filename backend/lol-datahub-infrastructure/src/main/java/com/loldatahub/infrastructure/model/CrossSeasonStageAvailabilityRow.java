package com.loldatahub.infrastructure.model;

import java.time.OffsetDateTime;

/**
 * 跨赛季赛段可用性查询结果行，包含赛季名称。
 */
public record CrossSeasonStageAvailabilityRow(
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
}
