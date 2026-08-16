package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.EloRatingQuery;
import com.loldatahub.domain.statistics.StageKey;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EloRatingServiceTest {
    private EloRatingService service;
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
        service = new EloRatingService(matchGameMapper, systemStateMapper,
                redisTemplate, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void replaysGamesInTimeOrderAndRatesTeams() {
        when(matchGameMapper.findAllGames(anyList())).thenReturn(List.of(
                game(1, 1, "2026-01-01T17:00", 1L, 2L, 1L),
                game(1, 2, "2026-01-01T18:00", 1L, 2L, 2L),
                game(2, 1, "2026-01-02T17:00", 1L, 2L, 1L)));

        EloRatingResult result = service.query(new EloRatingQuery(List.of(new StageKey(237, 106))));

        assertThat(result.totalGames()).isEqualTo(3);
        assertThat(result.ratings()).hasSize(2);
        EloRatingResult.TeamRating alpha = result.ratings().get(0);
        EloRatingResult.TeamRating beta = result.ratings().get(1);
        assertThat(alpha.teamId()).isEqualTo(1L);
        assertThat(alpha.rank()).isEqualTo(1);
        assertThat(alpha.games()).isEqualTo(3);
        assertThat(alpha.wins()).isEqualTo(2);
        assertThat(alpha.losses()).isEqualTo(1);
        assertThat(alpha.rating()).isGreaterThan(1500);
        assertThat(beta.rating()).isLessThan(1500);
        assertThat(beta.rank()).isEqualTo(2);
        // 零和：两队评分之和保持 3000
        assertThat((double) alpha.rating() + beta.rating()).isCloseTo(3000.0, within(1.0));
        assertThat(alpha.ratingHistory()).hasSize(3);
        assertThat(alpha.ratingHistory().get(0)).isCloseTo(1516.0, within(0.2));
    }

    @Test
    void equalRecordsShareRank() {
        when(matchGameMapper.findAllGames(anyList())).thenReturn(List.of(
                game(1, 1, "2026-01-01T17:00", 1L, 2L, 1L),
                game(2, 1, "2026-01-02T17:00", 3L, 4L, 3L)));

        EloRatingResult result = service.query(new EloRatingQuery(List.of(new StageKey(237, 106))));

        assertThat(result.ratings()).hasSize(4);
        // 两名胜方 1516 并列第 1，两名负方 1484 并列第 3
        assertThat(result.ratings().get(0).rank()).isEqualTo(1);
        assertThat(result.ratings().get(1).rank()).isEqualTo(1);
        assertThat(result.ratings().get(2).rank()).isEqualTo(3);
        assertThat(result.ratings().get(3).rank()).isEqualTo(3);
    }

    private static MatchTeamGameRow game(long matchId, int gameNumber, String startTime,
                                         long teamAId, long teamBId, long winnerTeamId) {
        return new MatchTeamGameRow(237, 106, matchId, gameNumber,
                LocalDateTime.parse(startTime), teamAId, "T" + teamAId, null,
                teamBId, "T" + teamBId, null, winnerTeamId);
    }
}
