package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StageSourceRecord(
        long stageId,
        String stageName,
        String startTime,
        String endTime
) {
}

