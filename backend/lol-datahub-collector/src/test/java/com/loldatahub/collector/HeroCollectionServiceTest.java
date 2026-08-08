package com.loldatahub.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.ChampionStatWriteMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeroCollectionServiceTest {

    private TjStatsClient client;
    private TjStatsResponseParser parser;
    private ObjectMapper objectMapper;
    private CollectionMapper collectionMapper;
    private ChampionStatWriteMapper writeMapper;
    private SystemStateMapper systemStateMapper;
    private CatalogCollectionService catalogCollectionService;
    private TransactionTemplate transactionTemplate;
    private HeroCollectionService service;

    @BeforeEach
    void setUp() {
        client = mock(TjStatsClient.class);
        parser = new TjStatsResponseParser(new ObjectMapper());
        objectMapper = new ObjectMapper();
        collectionMapper = mock(CollectionMapper.class);
        writeMapper = mock(ChampionStatWriteMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        catalogCollectionService = mock(CatalogCollectionService.class);
        transactionTemplate = mock(TransactionTemplate.class);

        service = new HeroCollectionService(
                client, parser, objectMapper, collectionMapper,
                writeMapper, systemStateMapper, catalogCollectionService, transactionTemplate
        );

        // insertRun 时设置 runId
        doAnswer(invocation -> {
            CollectionMapper.GeneratedId holder = invocation.getArgument(4);
            holder.setId(42L);
            return null;
        }).when(collectionMapper).insertRun(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    void parserFailure_rawResponseSaved_deleteNotCalled_runMarkedFailed() {
        // 上游返回空数组（结构性校验失败），parser 会抛 TjStatsSourceException
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": []}}
                """;
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("data.list 必须是非空数组");

        // 验证 raw response 已保存（在 parse 之前）
        verify(collectionMapper).insertRawResponse(
                eq(42L),
                eq("/compound/public/hero"),
                anyString(),
                eq(invalidJson),
                anyString(),
                any()
        );

        // 验证 deleteCurrentForStage 未被调用
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());

        // 验证 run 被标记为 FAILED
        ArgumentCaptor<String> statusCaptor = ArgumentCaptor.forClass(String.class);
        verify(collectionMapper).finishRun(
                eq(42L),
                statusCaptor.capture(),
                any(),
                eq(0),
                anyString()
        );
        assertThat(statusCaptor.getValue()).isEqualTo("FAILED");
    }

    @Test
    void parserFailure_businessValidation_rawResponseSaved_deleteNotCalled() {
        // heroId 为 0（业务校验失败）
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": [{"heroId": 0, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}]}}
                """;
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("heroId 必须大于 0");

        // 验证 raw response 已保存
        verify(collectionMapper).insertRawResponse(
                eq(42L),
                eq("/compound/public/hero"),
                anyString(),
                eq(invalidJson),
                anyString(),
                any()
        );

        // 验证 deleteCurrentForStage 未被调用
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());

        // 验证 run 被标记为 FAILED
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), contains("heroId 必须大于 0"));
    }

    @Test
    void validResponse_deleteCurrentForStageCalled_runMarkedSuccess() {
        String validJson = """
                {"success": true, "data": {"boCount": 10, "updatedAt": 1748345653, "gameVersion": ["15.10"], "list": [{"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 3, "bpCount": 8, "winningCount": 3, "totalKills": 10, "totalDeath": 5, "totalAssists": 15}]}}
                """;
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(validJson);
        // contentHash 不同，触发写入
        when(collectionMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");

        // mock transactionTemplate.execute
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        CollectionResult result = service.collect(1L, List.of(100L));

        // 验证 deleteCurrentForStage 被调用
        verify(writeMapper).deleteCurrentForStage(1L, 100L);

        // 验证 run 被标记为 SUCCESS
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    void multipleStages_parserFailsOnSecond_rawSavedForBoth_deleteNotCalled() {
        String validJson = """
                {"success": true, "data": {"boCount": 10, "updatedAt": 1748345653, "gameVersion": ["15.10"], "list": [{"heroId": 1, "heroCnName": "安妮", "pickCount": 5, "banCount": 3, "bpCount": 8, "winningCount": 3, "totalKills": 10, "totalDeath": 5, "totalAssists": 15}]}}
                """;
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": []}}
                """;

        // 第一个 stage 返回有效数据，第二个返回无效数据
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(validJson);
        when(client.fetchHeroStatistics(1L, 200L)).thenReturn(invalidJson);
        when(collectionMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");

        // mock transactionTemplate.execute
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            org.springframework.transaction.support.TransactionCallback<?> cb = inv.getArgument(0);
            return cb.doInTransaction(null);
        });

        assertThatThrownBy(() -> service.collect(1L, List.of(100L, 200L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("data.list 必须是非空数组");

        // 验证两个 stage 的 raw response 都已保存
        verify(collectionMapper).insertRawResponse(
                eq(42L), eq("/compound/public/hero"), anyString(), eq(validJson), anyString(), any()
        );
        verify(collectionMapper).insertRawResponse(
                eq(42L), eq("/compound/public/hero"), anyString(), eq(invalidJson), anyString(), any()
        );

        // 验证第二个 stage 的 deleteCurrentForStage 未被调用
        verify(writeMapper, never()).deleteCurrentForStage(1L, 200L);

        // 验证 run 被标记为 FAILED
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), anyInt(), anyString());
    }
}
