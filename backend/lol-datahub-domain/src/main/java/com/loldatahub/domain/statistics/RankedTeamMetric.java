package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 战队核心指标及同条件战队排名。口径与战队统计列表一致。
 */
public record RankedTeamMetric(
        String key,
        String label,
        BigDecimal value,
        String formattedValue,
        int rank,
        int cohortSize,
        boolean higherIsBetter
) {
}
