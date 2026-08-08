package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class PlayerStatisticsMath {
    private static final List<String> VALID_POSITIONS = List.of("TOP", "JUG", "MID", "AD", "SUP");

    private PlayerStatisticsMath() {
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

    public static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
    }

    public static String validatePosition(String position) {
        if (position == null || position.isBlank()) {
            return null;
        }
        String upper = position.trim().toUpperCase(Locale.ROOT);
        for (String valid : VALID_POSITIONS) {
            if (valid.equals(upper)) {
                return upper;
            }
        }
        throw new IllegalArgumentException("未知的选手位置：" + position);
    }
}
