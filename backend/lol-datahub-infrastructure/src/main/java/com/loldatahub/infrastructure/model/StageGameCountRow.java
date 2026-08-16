package com.loldatahub.infrastructure.model;

/**
 * 单个赛段的小局数量行：采集覆盖矩阵使用。
 */
public record StageGameCountRow(
        long sourceSeasonId,
        long sourceStageId,
        long games
) {
}
