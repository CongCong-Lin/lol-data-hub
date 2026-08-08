package com.loldatahub.domain.catalog;

import java.time.OffsetDateTime;

public record Season(
        long sourceSeasonId,
        String name,
        OffsetDateTime startTime,
        OffsetDateTime endTime,
        boolean open
) {
}

