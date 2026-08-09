package com.loldatahub.domain.statistics;

import java.util.Comparator;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection from(String value) {
        if (value == null || value.isBlank()) {
            return DESC;
        }
        return switch (value.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "asc" -> ASC;
            case "desc" -> DESC;
            default -> throw new IllegalArgumentException("不支持的排序方向：" + value);
        };
    }

    public <T> Comparator<T> apply(Comparator<T> comparator) {
        return this == DESC ? comparator.reversed() : comparator;
    }
}
