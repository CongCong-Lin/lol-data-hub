package com.loldatahub.domain.statistics;

import java.util.Comparator;

public enum SortDirection {
    ASC,
    DESC;

    public static SortDirection from(String value) {
        return "asc".equalsIgnoreCase(value) ? ASC : DESC;
    }

    public <T> Comparator<T> apply(Comparator<T> comparator) {
        return this == DESC ? comparator.reversed() : comparator;
    }
}
