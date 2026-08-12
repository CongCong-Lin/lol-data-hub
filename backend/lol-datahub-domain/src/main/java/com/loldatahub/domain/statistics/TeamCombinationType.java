package com.loldatahub.domain.statistics;

import java.util.Locale;

public enum TeamCombinationType {
    MID_JUNGLE("JUN", "MID"),
    BOT_SUPPORT("BOT", "SUP");

    private final String firstPosition;
    private final String secondPosition;

    TeamCombinationType(String firstPosition, String secondPosition) {
        this.firstPosition = firstPosition;
        this.secondPosition = secondPosition;
    }

    public String firstPosition() {
        return firstPosition;
    }

    public String secondPosition() {
        return secondPosition;
    }

    public static TeamCombinationType from(String value) {
        if (value == null || value.isBlank()) {
            return MID_JUNGLE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("不支持的组合类型：" + value);
        }
    }
}
