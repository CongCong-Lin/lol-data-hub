package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChampionStatisticsServiceTest {
    private ChampionStatisticsService service;

    @BeforeEach
    void setUp() {
        ChampionStatisticsMapper mapper = mock(ChampionStatisticsMapper.class);
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
}
