package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class TeamStatisticsMath {
    private TeamStatisticsMath() {
    }

    public static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal weightedAverage(BigDecimal weightedSum, long totalCount) {
        if (totalCount == 0) {
            return BigDecimal.ZERO;
        }
        return weightedSum.divide(BigDecimal.valueOf(totalCount), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal kda(long kills, long assists, long deaths) {
        return BigDecimal.valueOf(kills + assists)
                .divide(BigDecimal.valueOf(Math.max(deaths, 1L)), 6, RoundingMode.HALF_UP);
    }
}
