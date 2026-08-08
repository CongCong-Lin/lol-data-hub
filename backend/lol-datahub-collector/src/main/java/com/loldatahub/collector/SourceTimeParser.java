package com.loldatahub.collector;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

final class SourceTimeParser {
    private static final ZoneId CHINA = ZoneId.of("Asia/Shanghai");

    private SourceTimeParser() {
    }

    static OffsetDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            LocalDateTime local = LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            return local.atZone(CHINA).toOffsetDateTime();
        }
    }

    static OffsetDateTime fromEpochSeconds(java.time.Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}

