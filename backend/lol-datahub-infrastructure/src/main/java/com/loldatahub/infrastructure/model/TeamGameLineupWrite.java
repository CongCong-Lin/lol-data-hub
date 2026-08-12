package com.loldatahub.infrastructure.model;

import java.time.OffsetDateTime;

public record TeamGameLineupWrite(
        long runId,
        long seasonId,
        long stageId,
        long matchId,
        int gameNumber,
        long teamId,
        boolean won,
        long topChampionId,
        long jungleChampionId,
        long midChampionId,
        long botChampionId,
        long supportChampionId,
        OffsetDateTime collectedAt
) {
}
