package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 雷达图维度数据：同时提供原始值与同位置百分位归一化得分（0～100）。
 */
public record PlayerRadarMetric(
        String key,
        String label,
        BigDecimal value,
        BigDecimal averageValue,
        BigDecimal playerScore,
        BigDecimal averageScore,
        int rank,
        int cohortSize
) {
}
