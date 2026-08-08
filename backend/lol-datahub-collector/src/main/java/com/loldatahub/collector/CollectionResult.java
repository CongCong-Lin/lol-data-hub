package com.loldatahub.collector;

import java.util.List;

public record CollectionResult(
        long runId,
        String status,
        int changedRecords,
        List<Long> unchangedStageIds
) {
}

