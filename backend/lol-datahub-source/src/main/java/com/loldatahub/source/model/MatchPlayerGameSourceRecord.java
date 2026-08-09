package com.loldatahub.source.model;

public record MatchPlayerGameSourceRecord(
        long matchId,
        long bo,
        long playerId,
        String position,
        long heroId,
        String heroName,
        String heroTitle,
        long teamId,
        long winTeamId,
        long kill,
        long death,
        long assist
) {
}
