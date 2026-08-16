package com.loldatahub.infrastructure.model;

import java.time.LocalDateTime;

/**
 * 英雄统计快照精简行：版本窗口对比只关心可累加计数与快照时间。
 */
public record ChampionSnapshotRow(
        long sourceSeasonId,
        long sourceStageId,
        long sourceChampionId,
        long pickCount,
        long winningCount,
        LocalDateTime collectedAt
) {
}
