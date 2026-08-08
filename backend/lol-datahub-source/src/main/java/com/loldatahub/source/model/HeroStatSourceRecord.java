package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeroStatSourceRecord(
        long heroId,
        String heroName,
        String heroCnName,
        String heroCnTitle,
        String heroLogo,
        List<String> heroLocation,
        long pickCount,
        BigDecimal pickRate,
        long banCount,
        BigDecimal banRate,
        long winningCount,
        BigDecimal winningRate,
        long bpCount,
        BigDecimal bPRate,
        BigDecimal kDA,
        BigDecimal killPerGame,
        BigDecimal deathPerGame,
        BigDecimal assistPerGame,
        Long mostUsePlayerId,
        String mostUsePlayerName,
        long totalKills,
        long totalAssists,
        long totalDeath
) {
    public HeroStatSourceRecord {
        heroLocation = heroLocation == null ? List.of() : List.copyOf(heroLocation);
    }
}

