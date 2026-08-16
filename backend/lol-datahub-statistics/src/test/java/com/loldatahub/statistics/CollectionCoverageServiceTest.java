package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.infrastructure.mapper.CollectionCoverageMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.StageCatalogRow;
import com.loldatahub.infrastructure.model.StageGameCountRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CollectionCoverageServiceTest {
    private CollectionCoverageService service;
    private CollectionCoverageMapper coverageMapper;
    private CatalogMapper catalogMapper;

    @BeforeEach
    void setUp() {
        coverageMapper = mock(CollectionCoverageMapper.class);
        catalogMapper = mock(CatalogMapper.class);
        SystemStateMapper systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemStateMapper.currentDataVersion()).thenReturn(7L);
        service = new CollectionCoverageService(coverageMapper, catalogMapper, systemStateMapper,
                redisTemplate, new ObjectMapper(), Duration.ofHours(12));
    }

    @Test
    void mergesFourSourcesAndFlagsGaps() {
        when(coverageMapper.findHeroCollectedStages()).thenReturn(List.of(
                new StageKey(206, 91), new StageKey(237, 106)));
        when(coverageMapper.findTeamCollectedStages()).thenReturn(List.of(
                new StageKey(206, 91), new StageKey(237, 106)));
        when(coverageMapper.findPlayerCollectedStages()).thenReturn(List.of(
                new StageKey(206, 91), new StageKey(237, 106)));
        when(coverageMapper.countMatchGamesByStage()).thenReturn(List.of(
                new StageGameCountRow(237, 106, 12L)));
        when(catalogMapper.findStageCatalogRows(anyList())).thenReturn(List.of(
                new StageCatalogRow(206, 91, "2024职业联赛", "第二赛段淘汰赛"),
                new StageCatalogRow(237, 106, "2026职业联赛", "第三赛段组内赛")));

        CollectionCoverageResult result = service.query();

        assertThat(result.stages()).hasSize(2);
        // 按赛季倒序：237 在前
        CollectionCoverageResult.StageCoverage latest = result.stages().get(0);
        assertThat(latest.sourceSeasonId()).isEqualTo(237);
        assertThat(latest.heroCollected()).isTrue();
        assertThat(latest.teamCollected()).isTrue();
        assertThat(latest.playerCollected()).isTrue();
        assertThat(latest.matchGameCount()).isEqualTo(12L);

        CollectionCoverageResult.StageCoverage gap = result.stages().get(1);
        assertThat(gap.sourceSeasonId()).isEqualTo(206);
        assertThat(gap.stageName()).isEqualTo("第二赛段淘汰赛");
        assertThat(gap.matchGameCount()).isZero();
    }

    @Test
    void fallsBackToNumberedNamesWhenCatalogMisses() {
        when(coverageMapper.findHeroCollectedStages()).thenReturn(List.of(new StageKey(999, 1)));
        when(coverageMapper.findTeamCollectedStages()).thenReturn(List.of());
        when(coverageMapper.findPlayerCollectedStages()).thenReturn(List.of());
        when(coverageMapper.countMatchGamesByStage()).thenReturn(List.of());
        when(catalogMapper.findStageCatalogRows(anyList())).thenReturn(List.of());

        CollectionCoverageResult result = service.query();

        assertThat(result.stages()).hasSize(1);
        assertThat(result.stages().get(0).seasonName()).isEqualTo("赛季 #999");
        assertThat(result.stages().get(0).heroCollected()).isTrue();
        assertThat(result.stages().get(0).teamCollected()).isFalse();
    }
}
