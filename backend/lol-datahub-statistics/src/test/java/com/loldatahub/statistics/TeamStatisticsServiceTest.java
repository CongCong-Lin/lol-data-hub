package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.TeamStatistics;
import com.loldatahub.domain.statistics.TeamStatisticsQuery;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamStatisticsMapper;
import com.loldatahub.infrastructure.model.TeamAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class TeamStatisticsServiceTest {
    private TeamStatisticsService service;
    private TeamStatisticsMapper mapper;
    private SystemStateMapper systemStateMapper;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        mapper = mock(TeamStatisticsMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        service = new TeamStatisticsService(mapper, systemStateMapper, redisTemplate,
                new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void queryAggregatesAcrossStagesMapsFieldsAndMarksMinimumSample() {
        List<StageKey> requested = List.of(new StageKey(239, 28), new StageKey(237, 102));
        List<StageKey> normalized = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(mapper.findCollectedStageKeys(eq(normalized))).thenReturn(normalized);
        when(systemStateMapper.currentDataVersion()).thenReturn(7L);
        when(mapper.aggregateTeams(eq(normalized), eq(5))).thenReturn(List.of(
                new TeamAggregateRow(1L, "Alpha", "alpha.png", 6L, 12L, 4L, 60L, 30L, 50L, bd("240000"),
                        18000L, bd("720000"), 120L, 60L, 1800L, 20L, 30L, 4L, 6L, 40L, 18L, 5L,
                        bd("10.5"), bd("2.5"), bd("12000"), bd("0.5"), bd("2.0")),
                new TeamAggregateRow(2L, "Beta", "beta.png", 3L, 8L, 2L, 100L, 80L, null, null,
                        null, null, null, null, null, null, null, null, null, null, null, null,
                        bd("8"), bd("3"), bd("11000"), bd("1"), bd("1.5"))
        ));

        TeamStatisticsQuery query = new TeamStatisticsQuery(
                requested, 5, " TOTALKILLS ", SortDirection.DESC);

        TeamStatisticsResult result = service.query(query);

        assertThat(query.stages()).containsExactlyElementsOf(normalized);
        assertThat(query.sortBy()).isEqualTo("totalKills");
        assertThat(result.dataVersion()).isEqualTo(7L);
        assertThat(result.minimumMatchCount()).isEqualTo(5);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(TeamStatistics::teamName)
                .containsExactly("Beta", "Alpha");
        assertThat(result.items().get(0).sampleQualified()).isFalse();
        assertThat(result.items().get(1).sampleQualified()).isTrue();
        assertThat(result.items().get(1).winningRate()).isEqualByComparingTo("0.666667");
        assertThat(result.items().get(1).gameCount()).isEqualTo(12);
        assertThat(result.items().get(1).kda()).isEqualByComparingTo("3.666667");
        assertThat(result.items().get(1).killPerGame()).isEqualByComparingTo("5.000000");
        assertThat(result.items().get(1).damagePerGame()).isEqualByComparingTo("20000.000000");
        verify(mapper).aggregateTeams(eq(normalized), eq(5));
    }

    @Test
    void cacheHitReturnsCachedItemsWithoutAggregatingAgain() throws Exception {
        List<StageKey> stages = List.of(new StageKey(237, 100));
        when(mapper.findCollectedStageKeys(eq(stages))).thenReturn(stages);
        when(systemStateMapper.currentDataVersion()).thenReturn(9L);
        TeamStatistics cached = new TeamStatistics(11L, "Cached", "cached.png", 12L, 30L, 8L,
                bd("0.666667"), bd("3.0"), 120L, bd("10"), 60L, bd("5"), bd("20000"),
                bd("1800"), bd("400"), bd("0.4"), bd("0.2"), bd("0.6"), bd("0.5"), bd("0.4"),
                bd("666.7"), bd("7"), bd("4"), bd("2"), bd("3"), bd("1"),
                bd("12500"), bd("0.5"), bd("2"), true);
        when(valueOperations.get(anyString())).thenReturn(new ObjectMapper().writeValueAsString(List.of(cached)));

        TeamStatisticsQuery query = new TeamStatisticsQuery(
                stages, 10, "winningRate", SortDirection.DESC);

        TeamStatisticsResult result = service.query(query);

        assertThat(result.items()).containsExactly(cached);
        assertThat(result.total()).isEqualTo(1);
        verify(mapper, never()).aggregateTeams(any(), anyInt());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
