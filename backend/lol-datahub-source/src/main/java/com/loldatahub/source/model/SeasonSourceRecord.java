package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonSourceRecord(
        long seasonId,
        String seasonName,
        String startTime,
        String endTime,
        boolean openStatus
) {
}

