package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class StatisticsMath {
    private StatisticsMath() {
    }

    public static BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    public static BigDecimal perGame(long total, long matchCount) {
        if (matchCount == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(matchCount), 6, RoundingMode.HALF_UP);
    }
}

