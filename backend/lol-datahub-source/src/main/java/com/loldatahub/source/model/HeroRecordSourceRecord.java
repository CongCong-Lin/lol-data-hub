package com.loldatahub.source.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HeroRecordSourceRecord(
        @JsonProperty("heroID") long heroId,
        String heroName,
        String heroTitle,
        @JsonProperty("matchID") long matchId,
        long bo,
        String role,
        Boolean isRole,
        long kill,
        long death,
        long assist,
        @JsonProperty("teamID") long teamId,
        @JsonProperty("winTeamID") long winTeamId
) {
}
