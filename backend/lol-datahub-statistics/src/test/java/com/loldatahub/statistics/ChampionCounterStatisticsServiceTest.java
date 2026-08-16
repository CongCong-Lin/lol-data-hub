package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionCounterQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionCounterRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChampionCounterStatisticsServiceTest {
    private ChampionCounterStatisticsService service;
    private ChampionStatisticsMapper championStatisticsMapper;

    @BeforeEach
    void setUp() {
        championStatisticsMapper = mock(ChampionStatisticsMapper.class);
        SystemStateMapper systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemStateMapper.currentDataVersion()).thenReturn(7L);
        service = new ChampionCounterStatisticsService(championStatisticsMapper, systemStateMapper,
                redisTemplate, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void mapsOpponentRowsWithWinRates() {
        when(championStatisticsMapper.aggregateChampionCounters(anyList(), anyLong(), anyString(), anyInt()))
                .thenReturn(List.of(
                        new ChampionCounterRow(2L, "Ahri", "阿狸", "九尾妖狐", null, 10L, 7L),
                        new ChampionCounterRow(3L, "Syndra", "辛德拉", "暗黑元首", null, 5L, 1L)));

        ChampionCounterResult result = service.query(new ChampionCounterQuery(
                List.of(new StageKey(237, 106)), 1L, "MID", 2));

        assertThat(result.championId()).isEqualTo(1L);
        assertThat(result.position()).isEqualTo("MID");
        assertThat(result.totalGames()).isEqualTo(15L);
        assertThat(result.opponents()).hasSize(2);
        assertThat(result.opponents().get(0).championChineseName()).isEqualTo("阿狸");
        assertThat(result.opponents().get(0).winRate()).isEqualByComparingTo(new BigDecimal("0.700000"));
        assertThat(result.opponents().get(1).winRate()).isEqualByComparingTo(new BigDecimal("0.200000"));
    }

    @Test
    void queryRejectsUnknownPosition() {
        assertThatThrownBy(() -> new ChampionCounterQuery(
                List.of(new StageKey(237, 106)), 1L, "MIDDLE", 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("分路");
    }

    @Test
    void queryNormalizesPositionCase() {
        ChampionCounterQuery query = new ChampionCounterQuery(
                List.of(new StageKey(237, 106)), 1L, " jun ", 2);
        assertThat(query.position()).isEqualTo("JUN");
    }
}
