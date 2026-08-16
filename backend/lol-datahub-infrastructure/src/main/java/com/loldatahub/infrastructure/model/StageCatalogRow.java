package com.loldatahub.infrastructure.model;

/**
 * 赛段目录行：采集覆盖矩阵使用的赛季/赛段名称。
 */
public record StageCatalogRow(
        long sourceSeasonId,
        long sourceStageId,
        String seasonName,
        String stageName
) {
}
