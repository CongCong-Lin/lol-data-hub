package com.loldatahub.domain.catalog;

import java.time.OffsetDateTime;

public record Stage(
        long sourceSeasonId,
        long sourceStageId,
        String name,
        OffsetDateTime startTime,
        OffsetDateTime endTime
) {
}

