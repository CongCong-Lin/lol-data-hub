package com.loldatahub.statistics;

import java.util.List;

/**
 * 采集覆盖矩阵：每个已采集赛段在四类数据上的覆盖情况。
 */
public record CollectionCoverageResult(
        List<StageCoverage> stages
) {
    /** 单个赛段的覆盖行；matchGameCount 为已回填的小局数量。 */
    public record StageCoverage(
            long sourceSeasonId,
            long sourceStageId,
            String seasonName,
            String stageName,
            boolean heroCollected,
            boolean teamCollected,
            boolean playerCollected,
            long matchGameCount
    ) {
    }
}
