package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeasonStagesSourceRecord(
        long seasonId,
        String seasonName,
        List<StageSourceRecord> stageInfos
) {
    public SeasonStagesSourceRecord {
        stageInfos = stageInfos == null ? List.of() : List.copyOf(stageInfos);
    }
}

