package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerStatistics;
import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.PlayerStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerAggregateRow;
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

class PlayerStatisticsServiceTest {
    private PlayerStatisticsService service;
    private PlayerStatisticsMapper mapper;
    private SystemStateMapper systemStateMapper;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        mapper = mock(PlayerStatisticsMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        service = new PlayerStatisticsService(mapper, systemStateMapper, redisTemplate,
                new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void queryAggregatesAcrossStagesMapsFieldsAndMarksMinimumSample() {
        List<StageKey> requested = List.of(new StageKey(239, 28), new StageKey(237, 102));
        List<StageKey> normalized = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(mapper.findCollectedStageKeys(eq(normalized))).thenReturn(normalized);
        when(systemStateMapper.currentDataVersion()).thenReturn(8L);
        when(mapper.aggregatePlayers(eq(normalized), eq(5), eq(null))).thenReturn(List.of(
                new PlayerAggregateRow("p1", 11L, "Zed", "zed.png", "Team A,Team B", "TOP,MID",
                        8L, 20L, 2L, bd("10"), 80L, 40L, 20L, bd("12000"), bd("220"), bd("5"), bd("2"),
                        bd("65"), bd("100"), bd("28"), bd("48")),
                new PlayerAggregateRow("p2", 12L, "Alpha", "alpha.png", "Team C", "JUG",
                        2L, 5L, 0L, bd("1"), 100L, 20L, 50L, bd("11000"), bd("180"), bd("4"), bd("1"),
                        bd("55"), bd("-50"), bd("22"), bd("45"))
        ));

        PlayerStatisticsQuery query = new PlayerStatisticsQuery(
                requested, 5, null, " MATCHCOUNT ", SortDirection.DESC);

        PlayerStatisticsResult result = service.query(query);

        assertThat(query.stages()).containsExactlyElementsOf(normalized);
        assertThat(query.sortBy()).isEqualTo("matchCount");
        assertThat(result.dataVersion()).isEqualTo(8L);
        assertThat(result.minimumMatchCount()).isEqualTo(5);
        assertThat(result.total()).isEqualTo(2);
        assertThat(result.items()).extracting(PlayerStatistics::playerName)
                .containsExactly("Zed", "Alpha");
        assertThat(result.items().get(0).sampleQualified()).isTrue();
        assertThat(result.items().get(1).sampleQualified()).isFalse();
        assertThat(result.items().get(0).teamNames()).containsExactly("Team A", "Team B");
        assertThat(result.items().get(0).positions()).containsExactly("TOP", "MID");
        assertThat(result.items().get(0).kda()).isEqualByComparingTo("6.000000");
        assertThat(result.items().get(0).gameCount()).isEqualTo(20);
        assertThat(result.items().get(0).killPerGame()).isEqualByComparingTo("4.000000");
        verify(mapper).aggregatePlayers(eq(normalized), eq(5), eq(null));
    }

    @Test
    void positionFilterUsesNormalizedPositionBeforeSorting() {
        List<StageKey> stages = List.of(new StageKey(237, 100));
        when(mapper.findCollectedStageKeys(eq(stages))).thenReturn(stages);
        when(systemStateMapper.currentDataVersion()).thenReturn(3L);
        when(mapper.aggregatePlayers(eq(stages), eq(0), eq("TOP"))).thenReturn(List.of(
                new PlayerAggregateRow("p1", 11L, "Zed", null, "Team A", "TOP,MID",
                        1L, 3L, 0L, bd("0"), 1L, 1L, 1L, bd("1"), bd("1"), bd("1"), bd("1"),
                        bd("1"), bd("1"), bd("1"), bd("1"))
        ));

        PlayerStatisticsQuery query = new PlayerStatisticsQuery(
                stages, 0, " top ", "totalKills", SortDirection.DESC);

        PlayerStatisticsResult result = service.query(query);

        assertThat(query.position()).isEqualTo("TOP");
        assertThat(result.items()).extracting(PlayerStatistics::playerName)
                .containsExactly("Zed");
        verify(mapper).aggregatePlayers(eq(stages), eq(0), eq("TOP"));
    }

    @Test
    void cacheHitReturnsCachedItemsWithoutAggregatingAgain() throws Exception {
        List<StageKey> stages = List.of(new StageKey(237, 100));
        when(mapper.findCollectedStageKeys(eq(stages))).thenReturn(stages);
        when(systemStateMapper.currentDataVersion()).thenReturn(12L);
        PlayerStatistics cached = new PlayerStatistics("p-cache", 99L, "Cached", "cached.png",
                List.of("Team"), List.of("MID"), 10L, 25L, 2L, bd("5"), 70L, 40L, 20L,
                bd("5.5"), bd("7"), bd("4"), bd("2"), bd("12000"), bd("200"),
                bd("5"), bd("2"), bd("60"), bd("100"), bd("25"), bd("50"), true);
        when(valueOperations.get(anyString())).thenReturn(new ObjectMapper().writeValueAsString(List.of(cached)));

        PlayerStatisticsQuery query = new PlayerStatisticsQuery(
                stages, 5, null, "kda", SortDirection.DESC);

        PlayerStatisticsResult result = service.query(query);

        assertThat(result.items()).containsExactly(cached);
        assertThat(result.total()).isEqualTo(1);
        verify(mapper, never()).aggregatePlayers(any(), anyInt(), any());
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
