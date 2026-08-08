package com.loldatahub.source.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public record HeroStagePayload(
        long sampleBaseCount,
        Instant updatedAt,
        JsonNode gameVersion,
        List<HeroStatSourceRecord> heroes
) {
}

