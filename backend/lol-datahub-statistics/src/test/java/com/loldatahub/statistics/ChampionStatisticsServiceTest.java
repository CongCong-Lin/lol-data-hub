package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChampionStatisticsServiceTest {
    private ChampionStatisticsService service;
    private ChampionStatisticsMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = mock(ChampionStatisticsMapper.class);
        SystemStateMapper systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        service = new ChampionStatisticsService(mapper, systemStateMapper, redisTemplate,
                new ObjectMapper().findAndRegisterModules(), Duration.ofHours(12));
    }

    @Test
    void mergePositionsFromCsvDeduplicatesAcrossStages() {
        String csv = "[\"TOP\",\"MID\"]| [\"MID\",\"BOT\"]| [\"TOP\"]";

        var result = service.mergePositionsFromCsv(csv);

        assertThat(result).containsExactly("TOP", "MID", "BOT");
    }

    @Test
    void mergePositionsFromCsvHandlesNull() {
        assertThat(service.mergePositionsFromCsv(null)).isEmpty();
    }

    @Test
    void mergePositionsFromCsvHandlesBlank() {
        assertThat(service.mergePositionsFromCsv("  ")).isEmpty();
    }

    @Test
    void mergePositionsFromCsvHandlesSingleStage() {
        String csv = "[\"JUN\",\"SUP\"]";

        var result = service.mergePositionsFromCsv(csv);

        assertThat(result).containsExactly("JUN", "SUP");
    }

    @Test
    void actualPositionCsvUsesCanonicalLaneOrder() {
        assertThat(service.mergePositionsFromCsv("MID,TOP,SUP"))
                .containsExactly("TOP", "MID", "SUP");
    }

    @Test
    void positionFilterIsDelegatedAndMapsIndependentCounts() {
        StageKey stage = new StageKey(239, 18);
        when(mapper.findCollectedStageKeys(any())).thenReturn(List.of(stage));
        when(mapper.aggregateChampions(any(), anyInt(), eq("TOP"))).thenReturn(List.of(
                new ChampionAggregateRow(
                        50, "斯维因", "诺克萨斯统领", null, "TOP", "Zeus",
                        53, 2, 4, 6, 2, 11, 7, 19, null
                )
        ));
        var query = new com.loldatahub.domain.statistics.ChampionStatisticsQuery(
                List.of(stage), 0, "TOP", "winningRate",
                com.loldatahub.domain.statistics.SortDirection.DESC);

        ChampionStatisticsResult result = service.query(query);

        assertThat(result.items()).singleElement().satisfies(swain -> {
            assertThat(swain.positions()).containsExactly("TOP");
            assertThat(swain.pickCount()).isEqualTo(2);
            assertThat(swain.winningCount()).isEqualTo(2);
            assertThat(swain.totalKills()).isEqualTo(11);
        });
        verify(mapper).aggregateChampions(List.of(stage), 0, "TOP");
    }

    @Test
    void equalWinningRateUsesHigherPickCountAsTieBreakerForBothDirections() {
        StageKey stage = new StageKey(239, 18);
        when(mapper.findCollectedStageKeys(any())).thenReturn(List.of(stage));
        when(mapper.aggregateChampions(any(), anyInt(), eq(null))).thenReturn(List.of(
                aggregateRow(1, "低样本", 4, 2),
                aggregateRow(2, "中样本", 10, 5),
                aggregateRow(3, "高样本", 20, 10)
        ));

        for (var direction : com.loldatahub.domain.statistics.SortDirection.values()) {
            var query = new com.loldatahub.domain.statistics.ChampionStatisticsQuery(
                    List.of(stage), 0, null, "winningRate", direction);

            ChampionStatisticsResult result = service.query(query);

            assertThat(result.items())
                    .extracting(item -> item.pickCount())
                    .containsExactly(20L, 10L, 4L);
        }
    }

    @Test
    void missingCompositeStageKeyReportedPrecisely() {
        // 请求 237:102 和 239:28，但只有 237:102 已采集
        List<StageKey> requested = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(mapper.findCollectedStageKeys(any())).thenReturn(List.of(new StageKey(237, 102)));

        var query = new com.loldatahub.domain.statistics.ChampionStatisticsQuery(
                requested, 0, "bpRate", com.loldatahub.domain.statistics.SortDirection.DESC);

        assertThatThrownBy(() -> service.query(query))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("239:28")
                .hasMessageContaining("尚未采集");
    }

    private ChampionAggregateRow aggregateRow(long championId, String name, long picks, long wins) {
        return new ChampionAggregateRow(
                championId, name, null, null, "MID", null,
                100, picks, 0, picks, wins, 0, 0, 0, null
        );
    }
}
