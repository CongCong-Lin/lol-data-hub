package com.loldatahub.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.catalog.Stage;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogCollectionServiceTest {
    private TjStatsClient client;
    private CatalogMapper mapper;
    private CatalogCollectionService service;

    @BeforeEach
    void setUp() {
        client = mock(TjStatsClient.class);
        mapper = mock(CatalogMapper.class);
        service = new CatalogCollectionService(
                client,
                new TjStatsResponseParser(new ObjectMapper()),
                mapper
        );
        when(client.fetchSeasons()).thenReturn("""
                {"success":true,"data":[{
                  "seasonId":239,"seasonName":"2026季中冠军赛","openStatus":true
                },{
                  "seasonId":152,"seasonName":"2021季中冠军赛","openStatus":false
                }]}
                """);
    }

    @Test
    void rejectsGlobalStageFallbackWhoseSeasonDoesNotMatchRequest() {
        when(client.fetchStages(152L)).thenReturn("""
                {"success":true,"data":{"seasonId":0,"seasonName":"","stageInfos":[
                  {"stageId":1,"stageName":"春季赛常规赛"},
                  {"stageId":112,"stageName":"第一赛段组内赛"}
                ]}}
                """);

        assertThatThrownBy(() -> service.sync(152L))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("期望 152，实际 0");

        verify(mapper, never()).upsertStage(any());
    }

    @Test
    void persistsOnlyStagesReturnedForRequestedSeason() {
        when(client.fetchStages(239L)).thenReturn("""
                {"success":true,"data":{"seasonId":239,"seasonName":"2026季中冠军赛","stageInfos":[
                  {"stageId":28,"stageName":"入围赛"},
                  {"stageId":18,"stageName":"淘汰赛"}
                ]}}
                """);

        CatalogCollectionService.CatalogSyncResult result = service.sync(239L);

        assertThat(result.stageCount()).isEqualTo(2);
        assertThat(result.syncedSeasonIds()).containsExactly(239L);
        ArgumentCaptor<Stage> stages = ArgumentCaptor.forClass(Stage.class);
        verify(mapper, org.mockito.Mockito.times(2)).upsertStage(stages.capture());
        assertThat(stages.getAllValues())
                .extracting(Stage::sourceSeasonId, Stage::sourceStageId)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(239L, 28L),
                        org.assertj.core.groups.Tuple.tuple(239L, 18L)
                );
    }
}
