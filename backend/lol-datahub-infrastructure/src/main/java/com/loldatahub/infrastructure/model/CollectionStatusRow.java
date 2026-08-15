package com.loldatahub.infrastructure.model;

import java.time.LocalDateTime;

/**
 * 采集运行记录读取行：一次采集任务的类型、状态与变更量。
 */
public record CollectionStatusRow(
        long id,
        String collectionType,
        Long sourceSeasonId,
        String requestedStageIds,
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int changedRecords,
        String errorMessage
) {
}
