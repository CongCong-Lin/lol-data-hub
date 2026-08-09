package com.loldatahub.source.model;

import java.util.List;

public record PlayerHeroRecordPayload(
        long playerId,
        List<HeroRecordSourceRecord> records
) {
    public PlayerHeroRecordPayload {
        records = records == null ? List.of() : List.copyOf(records);
    }
}
