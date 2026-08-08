package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
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
import static org.mockito.Mockito.mock;
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
                new ObjectMapper(), Duration.ofHours(12));
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
}
