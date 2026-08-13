package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 选手详情页“职业场均对比”数据。
 *
 * <p>平均值和最高值都来自当前查询条件下、达到样本门槛的同位置选手，
 * 前端将平均值绘制在最高值柱内部，避免把两者误解为两个独立样本。</p>
 */
public record PlayerAverageContrastMetric(
        String key,
        String label,
        BigDecimal value,
        BigDecimal averageValue,
        BigDecimal maxValue,
        int rank,
        int cohortSize,
        boolean higherIsBetter,
        boolean percentage
) {
}
