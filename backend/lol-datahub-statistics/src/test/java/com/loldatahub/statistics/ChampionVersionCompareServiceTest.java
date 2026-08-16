package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionVersionCompareQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionCatalogRow;
import com.loldatahub.infrastructure.model.ChampionSnapshotRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChampionVersionCompareServiceTest {
    private ChampionVersionCompareService service;
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
        when(championStatisticsMapper.findChampionCatalog()).thenReturn(List.of(
                new ChampionCatalogRow(1L, "Ahri", "阿狸", null),
                new ChampionCatalogRow(2L, "Syndra", "辛德拉", null)));
        service = new ChampionVersionCompareService(championStatisticsMapper, systemStateMapper,
                redisTemplate, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void comparesSnapshotWindowsAndFallsBackToEarliest() {
        when(championStatisticsMapper.findChampionSnapshots(anyList())).thenReturn(List.of(
                snapshot(237, 100, 1L, 10, 5, "2026-01-01T00:00:00"),
                snapshot(237, 100, 1L, 20, 8, "2026-02-01T00:00:00"),
                snapshot(237, 100, 2L, 4, 3, "2026-01-15T00:00:00")));

        ChampionVersionCompareResult result = service.query(new ChampionVersionCompareQuery(
                List.of(new StageKey(237, 100)), LocalDate.parse("2026-01-10"), LocalDate.parse("2026-03-01")));

        assertThat(result.items()).hasSize(2);
        ChampionVersionCompareResult.Item ahri = result.items().stream()
                .filter(item -> item.championId() == 1L).findFirst().orElseThrow();
        // from 窗口（01-10）落在两次快照之间 → 取 01-01 快照；to 窗口取 02-01 快照
        assertThat(ahri.fromPickCount()).isEqualTo(10);
        assertThat(ahri.toPickCount()).isEqualTo(20);
        assertThat(ahri.pickDelta()).isEqualTo(10);
        assertThat(ahri.fromWinRate()).isEqualByComparingTo(new BigDecimal("0.500000"));
        assertThat(ahri.toWinRate()).isEqualByComparingTo(new BigDecimal("0.400000"));
        assertThat(ahri.winRateDelta()).isEqualByComparingTo(new BigDecimal("-0.100000"));

        ChampionVersionCompareResult.Item syndra = result.items().stream()
                .filter(item -> item.championId() == 2L).findFirst().orElseThrow();
        // to 窗口覆盖其唯一快照，from 窗口早于快照 → 回退到最早快照
        assertThat(syndra.fromPickCount()).isEqualTo(4);
        assertThat(syndra.toPickCount()).isEqualTo(4);
        assertThat(syndra.pickDelta()).isZero();
    }

    @Test
    void rejectsReversedDateRange() {
        assertThatThrownBy(() -> new ChampionVersionCompareQuery(
                List.of(new StageKey(237, 100)), LocalDate.parse("2026-03-01"), LocalDate.parse("2026-01-01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("早于");
    }

    private static ChampionSnapshotRow snapshot(long seasonId, long stageId, long championId,
                                                long pickCount, long winningCount, String collectedAt) {
        return new ChampionSnapshotRow(seasonId, stageId, championId, pickCount, winningCount,
                LocalDateTime.parse(collectedAt));
    }
}
