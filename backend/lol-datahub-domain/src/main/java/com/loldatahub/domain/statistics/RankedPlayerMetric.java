package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 带同位置排名的核心指标。排名采用竞赛排名：
 * rank = 1 + 同位置合格选手中严格优于当前选手的人数，数值并列获得相同排名。
 */
public record RankedPlayerMetric(
        String key,
        String label,
        BigDecimal value,
        String formattedValue,
        int rank,
        int cohortSize,
        boolean higherIsBetter
) {
}
