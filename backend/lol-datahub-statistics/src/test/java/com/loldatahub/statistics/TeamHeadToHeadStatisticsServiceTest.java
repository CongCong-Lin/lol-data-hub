package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamHeadToHeadQuery;
import com.loldatahub.infrastructure.mapper.MatchGameMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.MatchTeamGameRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamHeadToHeadStatisticsServiceTest {
    private TeamHeadToHeadStatisticsService service;
    private MatchGameMapper matchGameMapper;

    @BeforeEach
    void setUp() {
        matchGameMapper = mock(MatchGameMapper.class);
        SystemStateMapper systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemStateMapper.currentDataVersion()).thenReturn(7L);
        service = new TeamHeadToHeadStatisticsService(matchGameMapper, systemStateMapper,
                redisTemplate, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void aggregatesMatchAndGameRecordsPerOpponent() {
        when(matchGameMapper.findTeamGames(anyList(), anyLong())).thenReturn(List.of(
                game(9001, 1, "2026-01-01T17:00", 1L, "TES", 2L, "BLG", 1L),
                game(9001, 2, "2026-01-01T18:00", 1L, "TES", 2L, "BLG", 2L),
                game(9001, 3, "2026-01-01T19:00", 1L, "TES", 2L, "BLG", 1L),
                game(9002, 1, "2026-02-01T17:00", 1L, "TES", 2L, "BLG", 2L),
                game(9002, 2, "2026-02-01T18:00", 1L, "TES", 2L, "BLG", 2L),
                game(9003, 1, "2026-03-01T17:00", 1L, "TES", 3L, "WBG", 1L)));

        TeamHeadToHeadResult result = service.query(new TeamHeadToHeadQuery(
                List.of(new StageKey(237, 106)), 1L));

        assertThat(result.opponents()).hasSize(2);
        TeamHeadToHeadResult.Opponent blg = result.opponents().get(0);
        assertThat(blg.opponentTeamId()).isEqualTo(2L);
        assertThat(blg.matchCount()).isEqualTo(2);
        assertThat(blg.matchWins()).isEqualTo(1);
        assertThat(blg.matchLosses()).isEqualTo(1);
        assertThat(blg.gameCount()).isEqualTo(5);
        assertThat(blg.gameWins()).isEqualTo(2);
        assertThat(blg.gameLosses()).isEqualTo(3);
        assertThat(blg.opponentTeamName()).isEqualTo("BLG");

        TeamHeadToHeadResult.Opponent wbg = result.opponents().get(1);
        assertThat(wbg.opponentTeamId()).isEqualTo(3L);
        assertThat(wbg.matchWins()).isEqualTo(1);
        assertThat(wbg.gameCount()).isEqualTo(1);

        assertThat(result.recentMeetings()).hasSize(3);
        assertThat(result.recentMeetings().get(0).matchId()).isEqualTo(9003);
        assertThat(result.recentMeetings().get(0).won()).isTrue();
        assertThat(result.recentMeetings().get(1).matchId()).isEqualTo(9002);
        assertThat(result.recentMeetings().get(1).teamGameWins()).isEqualTo(0);
        assertThat(result.recentMeetings().get(1).opponentGameWins()).isEqualTo(2);
    }

    @Test
    void emptyGamesYieldEmptyResult() {
        when(matchGameMapper.findTeamGames(anyList(), anyLong())).thenReturn(List.of());
        TeamHeadToHeadResult result = service.query(new TeamHeadToHeadQuery(
                List.of(new StageKey(237, 106)), 1L));
        assertThat(result.opponents()).isEmpty();
        assertThat(result.recentMeetings()).isEmpty();
    }

    private static MatchTeamGameRow game(long matchId, int gameNumber, String startTime,
                                         long teamAId, String teamAName,
                                         long teamBId, String teamBName, long winnerTeamId) {
        return new MatchTeamGameRow(237, 106, matchId, gameNumber,
                LocalDateTime.parse(startTime), teamAId, teamAName, null,
                teamBId, teamBName, null, winnerTeamId);
    }
}
