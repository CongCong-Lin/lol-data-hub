package com.loldatahub.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatWriteMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerStageStatWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlayerCollectionServiceTest {
    private TjStatsClient client;
    private CollectionMapper collectionMapper;
    private PlayerStatWriteMapper writeMapper;
    private PlayerStatisticsMapper statisticsMapper;
    private SystemStateMapper systemStateMapper;
    private TransactionTemplate transactionTemplate;
    private PlayerCollectionService service;

    @BeforeEach
    void setUp() {
        client = mock(TjStatsClient.class);
        collectionMapper = mock(CollectionMapper.class);
        writeMapper = mock(PlayerStatWriteMapper.class);
        statisticsMapper = mock(PlayerStatisticsMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        CatalogCollectionService catalog = mock(CatalogCollectionService.class);
        transactionTemplate = mock(TransactionTemplate.class);

        service = new PlayerCollectionService(
                client, new TjStatsResponseParser(new ObjectMapper()), new ObjectMapper(),
                collectionMapper, writeMapper, statisticsMapper, systemStateMapper,
                catalog, transactionTemplate
        );
        doAnswer(invocation -> {
            CollectionMapper.GeneratedId holder = invocation.getArgument(4);
            holder.setId(42L);
            return null;
        }).when(collectionMapper).insertRun(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    void validResponseWritesCurrentAndSnapshotAndFinishesSuccess() {
        String json = validJson();
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(json);
        when(statisticsMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");
        executeTransactionsImmediately();

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.changedRecords()).isEqualTo(2);
        verify(writeMapper).upsertCollectionCurrent(eq(1L), eq(100L), anyString(), any(), eq(42L));
        verify(writeMapper).deleteCurrentForStage(1L, 100L);
        verify(writeMapper).upsertPlayer(any());
        verify(writeMapper).upsertCurrent(any());
        verify(writeMapper).insertSnapshot(any());
        verify(systemStateMapper).incrementDataVersion();

        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(collectionMapper).finishRun(eq(42L), status.capture(), any(), eq(2), isNull());
        assertThat(status.getValue()).isEqualTo("SUCCESS");
    }

    @Test
    void usesExactPercentagesFromMatchDetailsWhenCoverageIsComplete() {
        String json = """
                {"success":true,"data":[{"playerId":12345,"teamId":100,"playerName":"JackeyLove",
                "teamName":"TES","matchCount":1,"boCount":1,"mvpCount":0,"mvpVotes":0,
                "totalKills":2,"totalAssists":1,"totalDeath":1,"killParticipantPercent":0.60,
                "damagePercent":0.25,"goldPercent":0.25}]}
                """;
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(json);
        when(collectionMapper.findPlayerHeroRecordResponse(1L, 100L, 12345L))
                .thenReturn(exactHeroRecordJson());
        when(collectionMapper.findMatchDetailResponses(1L, 100L))
                .thenReturn(List.of(exactMatchDetailJson()
                        .replace("\"playerId\":12345", "\"playerId\":11")));
        when(client.fetchMatchDetail(9984L)).thenReturn(exactMatchDetailJson());
        when(statisticsMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");
        executeTransactionsImmediately();

        service.collect(1L, List.of(100L));

        ArgumentCaptor<PlayerStageStatWrite> captor = ArgumentCaptor.forClass(PlayerStageStatWrite.class);
        verify(writeMapper).upsertCurrent(captor.capture());
        PlayerStageStatWrite stat = captor.getValue();
        assertThat(stat.sourceKillParticipantPercent()).isEqualByComparingTo("0.4285714285714285714285714285714286");
        assertThat(stat.sourceDamagePercent()).isEqualByComparingTo("0.26");
        assertThat(stat.sourceGoldPercent()).isEqualByComparingTo("0.251");
        verify(client).fetchMatchDetail(9984L);
        verify(collectionMapper).insertRawResponse(
                eq(42L), eq("/compound/matchDetail"), anyString(),
                eq(exactMatchDetailJson()), anyString(), any());
    }

    @Test
    void parserOrBusinessValidationFailureDoesNotDeleteCurrentAndMarksRunFailed() {
        String invalidJson = """
                {"success":true,"data":[{"playerId":12345,"playerName":"","matchCount":20,
                "boCount":55,"mvpCount":5,"mvpVotes":100,"totalKills":150,"totalAssists":200,"totalDeath":50}]}
                """;
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class);

        verify(collectionMapper).insertRawResponse(
                eq(42L), eq("/compound/public/player"), anyString(), eq(invalidJson), anyString(), any()
        );
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).upsertCurrent(any());
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), anyString());
        verify(systemStateMapper, never()).incrementDataVersion();
    }

    @Test
    void sameHashSkipsWritesAndFinishesNoChange() {
        String json = validJson();
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(json);
        when(statisticsMapper.findCurrentContentHash(1L, 100L)).thenReturn(
                sha256(PlayerCollectionService.CONTENT_SCHEMA_VERSION + "\n"
                        + hashMaterial("/compound/public/player", json)));

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("NO_CHANGE");
        assertThat(result.changedRecords()).isZero();
        assertThat(result.unchangedStageIds()).containsExactly(100L);
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).upsertCollectionCurrent(anyLong(), anyLong(), anyString(), any(), anyLong());
        verify(writeMapper, never()).upsertCurrent(any());
        verify(writeMapper, never()).insertSnapshot(any());
        verify(collectionMapper).finishRun(eq(42L), eq("NO_CHANGE"), any(), eq(0), isNull());
        verify(systemStateMapper, never()).incrementDataVersion();
    }

    @Test
    void secondStageValidationFailureDoesNotPublishFirstStage() {
        String validJson = validJson();
        String invalidJson = """
                {"success":true,"data":[{"playerId":12345,"playerName":"","matchCount":20,
                "boCount":55,"mvpCount":5,"mvpVotes":100,"totalKills":150,"totalAssists":200,"totalDeath":50}]}
                """;
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(validJson);
        when(client.fetchPlayerStatistics(1L, 200L)).thenReturn(invalidJson);
        when(statisticsMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");

        assertThatThrownBy(() -> service.collect(1L, List.of(100L, 200L)))
                .isInstanceOf(TjStatsSourceException.class);

        verify(collectionMapper, times(2)).insertRawResponse(
                eq(42L), eq("/compound/public/player"), anyString(), anyString(), anyString(), any()
        );
        verify(transactionTemplate, never()).execute(any());
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(systemStateMapper, never()).incrementDataVersion();
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), anyString());
    }

    @Test
    void multipleChangedStagesUseOnePublishTransaction() {
        String json = validJson();
        when(client.fetchPlayerStatistics(1L, 100L)).thenReturn(json);
        when(client.fetchPlayerStatistics(1L, 200L)).thenReturn(json);
        when(statisticsMapper.findCurrentContentHash(anyLong(), anyLong())).thenReturn("different-hash");
        executeTransactionsImmediately();

        CollectionResult result = service.collect(1L, List.of(200L, 100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.changedRecords()).isEqualTo(4);
        verify(transactionTemplate, times(1)).execute(any());
        verify(writeMapper).deleteCurrentForStage(1L, 100L);
        verify(writeMapper).deleteCurrentForStage(1L, 200L);
        verify(systemStateMapper, times(1)).incrementDataVersion();
        verify(collectionMapper).finishRun(eq(42L), eq("SUCCESS"), any(), eq(4), isNull());
    }

    private void executeTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private static String validJson() {
        return """
                {"success":true,"data":[{"playerId":12345,"teamId":100,"playerName":"JackeyLove",
                "teamName":"TES","matchCount":20,"boCount":55,"mvpCount":5,"mvpVotes":100,
                "totalKills":150,"totalAssists":200,"totalDeath":50}]}
                """;
    }

    private static String exactMatchDetailJson() {
        return """
                {"success":true,"data":{"matchId":9984,"matchInfos":[{"bo":1,"teamInfos":[
                  {"teamId":100,"kills":7,"golds":1000,"playerInfos":[
                    {"playerId":12345,"battleDetail":{"kills":2,"death":1,"assist":1},"damageDetail":{"heroDamage":26},"otherDetail":{"golds":251}},
                    {"playerId":2,"battleDetail":{"kills":2,"death":1,"assist":0},"damageDetail":{"heroDamage":18.5},"otherDetail":{"golds":187.25}},
                    {"playerId":3,"battleDetail":{"kills":1,"death":1,"assist":0},"damageDetail":{"heroDamage":18.5},"otherDetail":{"golds":187.25}},
                    {"playerId":4,"battleDetail":{"kills":1,"death":1,"assist":0},"damageDetail":{"heroDamage":18.5},"otherDetail":{"golds":187.25}},
                    {"playerId":5,"battleDetail":{"kills":1,"death":1,"assist":0},"damageDetail":{"heroDamage":18.5},"otherDetail":{"golds":187.25}}]},
                  {"teamId":200,"kills":3,"golds":900,"playerInfos":[
                    {"playerId":6,"battleDetail":{"kills":1,"death":0,"assist":0},"damageDetail":{"heroDamage":20},"otherDetail":{"golds":180}},
                    {"playerId":7,"battleDetail":{"kills":1,"death":0,"assist":0},"damageDetail":{"heroDamage":20},"otherDetail":{"golds":180}},
                    {"playerId":8,"battleDetail":{"kills":1,"death":0,"assist":0},"damageDetail":{"heroDamage":20},"otherDetail":{"golds":180}},
                    {"playerId":9,"battleDetail":{"kills":0,"death":1,"assist":0},"damageDetail":{"heroDamage":20},"otherDetail":{"golds":180}},
                    {"playerId":10,"battleDetail":{"kills":0,"death":1,"assist":0},"damageDetail":{"heroDamage":20},"otherDetail":{"golds":180}}]}
                ]}]}}
                """;
    }

    private static String exactHeroRecordJson() {
        return """
                {"success":true,"data":{"playerID":12345,"heroRecordList":[{
                  "heroID":1,"heroName":"Annie","heroTitle":"黑暗之女",
                  "matchID":9984,"bo":1,"role":"MID","isRole":true,
                  "kill":2,"death":1,"assist":1,"teamID":100,"winTeamID":100
                }]}}
                """;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static String hashMaterial(String label, String value) {
        return label.length() + ":" + label + ":" + value.length() + ":" + value;
    }
}
