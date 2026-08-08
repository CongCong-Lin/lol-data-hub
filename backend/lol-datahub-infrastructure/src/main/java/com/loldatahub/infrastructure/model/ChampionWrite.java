package com.loldatahub.infrastructure.model;

public record ChampionWrite(
        long championId,
        String internalName,
        String chineseName,
        String chineseTitle,
        String logoUrl,
        String positionsJson
) {
}

