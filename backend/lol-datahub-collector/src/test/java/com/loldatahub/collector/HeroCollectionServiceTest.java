package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.ChampionStatWriteMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionPositionPlayerStageStatWrite;
import com.loldatahub.infrastructure.model.ChampionWrite;
import com.loldatahub.infrastructure.model.TeamGameLineupWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HeroCollectionServiceTest {
    private static final List<String> RECORD_POSITIONS = List.of("TOP", "JUN", "MID", "BOT", "SUP");
    private static final List<String> PLAYER_POSITIONS = List.of("TOP", "JUG", "MID", "AD", "SUP");

    private TjStatsClient client;
    private ObjectMapper objectMapper;
    private CollectionMapper collectionMapper;
    private ChampionStatWriteMapper writeMapper;
    private SystemStateMapper systemStateMapper;
    private TransactionTemplate transactionTemplate;
    private HeroCollectionService service;

    @BeforeEach
    void setUp() {
        client = mock(TjStatsClient.class);
        objectMapper = new ObjectMapper();
        collectionMapper = mock(CollectionMapper.class);
        writeMapper = mock(ChampionStatWriteMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        CatalogCollectionService catalogCollectionService = mock(CatalogCollectionService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        service = new HeroCollectionService(
                client, new TjStatsResponseParser(objectMapper), objectMapper, collectionMapper,
                writeMapper, systemStateMapper, catalogCollectionService, transactionTemplate
        );
        doAnswer(invocation -> {
            CollectionMapper.GeneratedId holder = invocation.getArgument(4);
            holder.setId(42L);
            return null;
        }).when(collectionMapper).insertRun(anyString(), anyLong(), anyString(), any(), any());
    }

    @Test
    void parserFailureRawResponseSavedAndCurrentDataUntouched() {
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": []}}
                """;
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("data.list 必须是非空数组");

        verify(collectionMapper).insertRawResponse(
                eq(42L), eq("/compound/public/hero"), anyString(), eq(invalidJson), anyString(), any()
        );
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).deletePositionCurrentForStage(anyLong(), anyLong());
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), anyString());
    }

    @Test
    void businessValidationFailureDoesNotDeleteCurrentData() {
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": [{"heroId": 0, "heroCnName": "安妮", "pickCount": 0, "banCount": 0, "bpCount": 0, "winningCount": 0, "totalKills": 0, "totalDeath": 0, "totalAssists": 0}]}}
                """;
        when(client.fetchHeroStatistics(1L, 100L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("heroId 必须大于 0");

        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).deletePositionCurrentForStage(anyLong(), anyLong());
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), contains("heroId 必须大于 0"));
    }

    @Test
    void validResponsesPublishAggregateAndActualPositionRows() {
        mockValidStage(100L, false, false);
        when(collectionMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");
        executeTransactionsImmediately();

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.changedRecords()).isEqualTo(23);
        verify(writeMapper).deletePositionCurrentForStage(1L, 100L);
        verify(writeMapper).deleteCurrentForStage(1L, 100L);
        verify(writeMapper, times(10)).upsertPositionCurrent(any());
        verify(writeMapper, times(10)).insertPositionSnapshot(any());
        ArgumentCaptor<TeamGameLineupWrite> lineups = ArgumentCaptor.forClass(TeamGameLineupWrite.class);
        verify(writeMapper, times(2)).upsertTeamGameLineup(lineups.capture());
        verify(writeMapper, times(2)).insertTeamGameLineupSnapshot(any());
        assertThat(lineups.getAllValues()).extracting(TeamGameLineupWrite::teamId)
                .containsExactly(1L, 2L);
        assertThat(lineups.getAllValues().getFirst().jungleChampionId()).isEqualTo(2L);
        assertThat(lineups.getAllValues().getFirst().midChampionId()).isEqualTo(3L);
        verify(systemStateMapper).incrementDataVersion();
        verify(collectionMapper).finishRun(eq(42L), eq("SUCCESS"), any(), eq(23), isNull());
    }

    @Test
    void blankHistoricalRolesUsePerGameMatchDetails() {
        mockValidStage(100L, false, false);
        for (long playerId = 1; playerId <= 10; playerId++) {
            when(client.fetchPlayerHeroRecords(playerId, 1L, 100L))
                    .thenReturn(heroRecordJson(playerId, 100L, false, true));
        }
        when(client.fetchMatchDetail(1100L)).thenReturn(matchDetailJson(1100L));
        when(collectionMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");
        executeTransactionsImmediately();

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<ChampionPositionPlayerStageStatWrite> rows =
                ArgumentCaptor.forClass(ChampionPositionPlayerStageStatWrite.class);
        verify(writeMapper, times(10)).upsertPositionCurrent(rows.capture());
        assertThat(rows.getAllValues())
                .extracting(ChampionPositionPlayerStageStatWrite::position)
                .containsExactlyInAnyOrder("TOP", "JUN", "MID", "BOT", "SUP",
                        "TOP", "JUN", "MID", "BOT", "SUP");
    }

    @Test
    void internalHeroNameFallsBackToRequiredDisplayName() {
        mockValidStage(100L, true, false);
        when(collectionMapper.findCurrentContentHash(1L, 100L)).thenReturn("different-hash");
        executeTransactionsImmediately();

        service.collect(1L, List.of(100L));

        ArgumentCaptor<ChampionWrite> champions = ArgumentCaptor.forClass(ChampionWrite.class);
        verify(writeMapper, times(10)).upsertChampion(champions.capture());
        ChampionWrite annie = champions.getAllValues().stream()
                .filter(champion -> champion.championId() == 1L)
                .findFirst()
                .orElseThrow();
        assertThat(annie.internalName()).isEqualTo("Annie");
        assertThat(annie.chineseName()).isEqualTo("Annie");
        assertThat(annie.logoUrl()).isEqualTo("https://game.gtimg.cn/images/lol/Annie.png");
        assertThat(annie.positionsJson()).isEqualTo("[\"TOP\"]");
    }

    @Test
    void multipleStagesFailBeforePublishWhenSecondHeroResponseIsInvalid() {
        mockValidStage(100L, false, false);
        String invalidJson = """
                {"success": true, "data": {"boCount": 10, "list": []}}
                """;
        when(client.fetchHeroStatistics(1L, 200L)).thenReturn(invalidJson);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L, 200L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("data.list 必须是非空数组");

        verify(transactionTemplate, never()).execute(any());
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).deletePositionCurrentForStage(anyLong(), anyLong());
        verify(systemStateMapper, never()).incrementDataVersion();
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), anyString());
    }

    @Test
    void multipleChangedStagesUseOnePublishTransaction() {
        mockValidStage(100L, false, false);
        mockValidStage(200L, false, false);
        when(collectionMapper.findCurrentContentHash(anyLong(), anyLong())).thenReturn("different-hash");
        executeTransactionsImmediately();

        CollectionResult result = service.collect(1L, List.of(200L, 100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.changedRecords()).isEqualTo(46);
        verify(transactionTemplate, times(1)).execute(any());
        verify(writeMapper).deleteCurrentForStage(1L, 100L);
        verify(writeMapper).deleteCurrentForStage(1L, 200L);
        verify(systemStateMapper, times(1)).incrementDataVersion();
    }

    @Test
    void mismatchedPerGameStatisticsAreRejectedBeforeDelete() {
        mockValidStage(100L, false, true);

        assertThatThrownBy(() -> service.collect(1L, List.of(100L)))
                .isInstanceOf(TjStatsSourceException.class)
                .hasMessageContaining("逐局合计与官网聚合不一致")
                .hasMessageContaining("heroId=1");

        verify(transactionTemplate, never()).execute(any());
        verify(writeMapper, never()).deleteCurrentForStage(anyLong(), anyLong());
        verify(writeMapper, never()).deletePositionCurrentForStage(anyLong(), anyLong());
        verify(collectionMapper).finishRun(eq(42L), eq("FAILED"), any(), eq(0), anyString());
    }

    private void mockValidStage(long stageId, boolean firstHeroHasOnlyInternalName, boolean corruptFirstKill) {
        when(client.fetchHeroStatistics(1L, stageId))
                .thenReturn(heroJson(firstHeroHasOnlyInternalName));
        when(client.fetchPlayerStatistics(1L, stageId)).thenReturn(playerJson());
        for (long playerId = 1; playerId <= 10; playerId++) {
            when(client.fetchPlayerHeroRecords(playerId, 1L, stageId))
                    .thenReturn(heroRecordJson(playerId, stageId, corruptFirstKill && playerId == 1));
        }
    }

    private void executeTransactionsImmediately() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private String heroJson(boolean firstHeroHasOnlyInternalName) {
        List<Map<String, Object>> heroes = new ArrayList<>();
        for (long heroId = 1; heroId <= 10; heroId++) {
            Map<String, Object> hero = new LinkedHashMap<>();
            hero.put("heroId", heroId);
            hero.put("heroName", heroId == 1 ? "Annie" : "Hero" + heroId);
            if (!(firstHeroHasOnlyInternalName && heroId == 1)) {
                hero.put("heroCnName", heroId == 1 ? "安妮" : "英雄" + heroId);
            }
            if (heroId == 1) {
                hero.put("heroLogo", "http://game.gtimg.cn/images/lol/Annie.png");
            }
            hero.put("pickCount", 1);
            hero.put("banCount", 0);
            hero.put("bpCount", 1);
            hero.put("winningCount", heroId <= 5 ? 1 : 0);
            hero.put("totalKills", 1);
            hero.put("totalDeath", 0);
            hero.put("totalAssists", 2);
            heroes.add(hero);
        }
        return json(Map.of("success", true, "data", Map.of(
                "boCount", 1,
                "updatedAt", 1748345653,
                "gameVersion", List.of("15.10"),
                "list", heroes
        )));
    }

    private String playerJson() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (long playerId = 1; playerId <= 10; playerId++) {
            players.add(Map.of(
                    "playerId", playerId,
                    "playerName", "Player" + playerId,
                    "playerLocation", PLAYER_POSITIONS.get((int) ((playerId - 1) % 5)),
                    "matchCount", 1,
                    "boCount", 1,
                    "mvpCount", 0,
                    "mvpVotes", 0,
                    "totalKills", 1,
                    "totalAssists", 2,
                    "totalDeath", 0
            ));
        }
        return json(Map.of("success", true, "data", players));
    }

    private String heroRecordJson(long playerId, long stageId, boolean corruptKill) {
        return heroRecordJson(playerId, stageId, corruptKill, false);
    }

    private String heroRecordJson(long playerId, long stageId, boolean corruptKill, boolean blankRole) {
        String position = RECORD_POSITIONS.get((int) ((playerId - 1) % 5));
        long teamId = playerId <= 5 ? 1 : 2;
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("heroID", playerId);
        record.put("heroName", "Hero" + playerId);
        record.put("matchID", 1000 + stageId);
        record.put("bo", 1);
        record.put("role", blankRole ? "" : position);
        record.put("isRole", true);
        record.put("kill", corruptKill ? 99 : 1);
        record.put("death", 0);
        record.put("assist", 2);
        record.put("teamID", teamId);
        record.put("winTeamID", 1);
        return json(Map.of("success", true, "data", Map.of(
                "playerID", playerId,
                "heroRecordList", List.of(record)
        )));
    }

    private String matchDetailJson(long matchId) {
        List<Map<String, Object>> teamOnePlayers = new ArrayList<>();
        List<Map<String, Object>> teamTwoPlayers = new ArrayList<>();
        for (long playerId = 1; playerId <= 10; playerId++) {
            Map<String, Object> player = Map.of(
                    "playerId", playerId,
                    "playerLocation", RECORD_POSITIONS.get((int) ((playerId - 1) % 5)),
                    "heroId", playerId,
                    "heroName", "Hero" + playerId,
                    "heroTitle", "Title" + playerId,
                    "battleDetail", Map.of("kills", 1, "death", 0, "assist", 2)
            );
            (playerId <= 5 ? teamOnePlayers : teamTwoPlayers).add(player);
        }
        return json(Map.of("success", true, "data", Map.of(
                "matchId", matchId,
                "matchInfos", List.of(Map.of(
                        "bo", 1,
                        "matchWin", 1,
                        "teamInfos", List.of(
                                Map.of("teamId", 1, "playerInfos", teamOnePlayers),
                                Map.of("teamId", 2, "playerInfos", teamTwoPlayers)
                        )
                ))
        )));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
