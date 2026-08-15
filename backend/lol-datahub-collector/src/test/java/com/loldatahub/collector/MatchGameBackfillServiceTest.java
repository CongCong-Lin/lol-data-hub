package com.loldatahub.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.MatchGameWriteMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.MatchDetailSourceRow;
import com.loldatahub.infrastructure.model.MatchGameWrite;
import com.loldatahub.source.TjStatsResponseParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchGameBackfillServiceTest {
    private CollectionMapper collectionMapper;
    private MatchGameWriteMapper writeMapper;
    private SystemStateMapper systemStateMapper;
    private TransactionTemplate transactionTemplate;
    private MatchGameBackfillService service;

    @BeforeEach
    void setUp() {
        collectionMapper = mock(CollectionMapper.class);
        writeMapper = mock(MatchGameWriteMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        transactionTemplate = mock(TransactionTemplate.class);

        service = new MatchGameBackfillService(
                new TjStatsResponseParser(new ObjectMapper()), new ObjectMapper(),
                collectionMapper, writeMapper, systemStateMapper, transactionTemplate
        );
        doAnswer(invocation -> {
            CollectionMapper.GeneratedId holder = invocation.getArgument(4);
            holder.setId(42L);
            return null;
        }).when(collectionMapper).insertRun(anyString(), anyLong(), anyString(), any(), any());
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    @Test
    void perGameStartTimesFromMatchInfosAreWritten() {
        String json = matchDetailJson(
                "2025-04-20T20:29:46+08:00", "2025-04-20T21:19:34+08:00", null);
        assertThat(json).contains("\"matchStartTime\":\"2025-04-20T20:29:46+08:00\"");
        when(collectionMapper.findLatestMatchDetails(1L, 100L))
                .thenReturn(List.of(new MatchDetailSourceRow(1L, 9984L, json)));

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<MatchGameWrite> captor = ArgumentCaptor.forClass(MatchGameWrite.class);
        verify(writeMapper, org.mockito.Mockito.times(2)).upsertMatchGame(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MatchGameWrite::startTime)
                .containsExactlyInAnyOrder(
                        LocalDateTime.of(2025, 4, 20, 20, 29, 46),
                        LocalDateTime.of(2025, 4, 20, 21, 19, 34));
    }

    @Test
    void fallsBackToTopLevelMatchTimeWhenPerGameTimesMissing() {
        when(collectionMapper.findLatestMatchDetails(1L, 100L))
                .thenReturn(List.of(new MatchDetailSourceRow(1L, 9984L, matchDetailJson(
                        null, null, "2025-04-20T19:00:01+08:00"))));

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<MatchGameWrite> captor = ArgumentCaptor.forClass(MatchGameWrite.class);
        verify(writeMapper, org.mockito.Mockito.times(2)).upsertMatchGame(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MatchGameWrite::startTime)
                .containsOnly(LocalDateTime.of(2025, 4, 20, 19, 0, 1));
    }

    @Test
    void keepsNullStartTimeWhenNoTimeFieldPresent() {
        when(collectionMapper.findLatestMatchDetails(1L, 100L))
                .thenReturn(List.of(new MatchDetailSourceRow(1L, 9984L, matchDetailJson(
                        null, null, null))));

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<MatchGameWrite> captor = ArgumentCaptor.forClass(MatchGameWrite.class);
        verify(writeMapper, org.mockito.Mockito.times(2)).upsertMatchGame(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MatchGameWrite::startTime)
                .containsOnly((LocalDateTime) null);
    }

    @Test
    void bumpsDataVersionOnSuccessfulBackfill() {
        when(collectionMapper.findLatestMatchDetails(1L, 100L))
                .thenReturn(List.of(new MatchDetailSourceRow(1L, 9984L, matchDetailJson(
                        "2025-04-20T20:29:46+08:00", "2025-04-20T21:19:34+08:00", null))));

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("SUCCESS");
        verify(systemStateMapper).incrementDataVersion();
    }

    @Test
    void skipsVersionBumpWhenNothingToWrite() {
        when(collectionMapper.findLatestMatchDetails(1L, 100L))
                .thenReturn(List.of());

        CollectionResult result = service.collect(1L, List.of(100L));

        assertThat(result.status()).isEqualTo("NO_CHANGE");
        verify(systemStateMapper, never()).incrementDataVersion();
    }

    private static String matchDetailJson(String bo1Start, String bo2Start, String matchTime) {
        return """
                {"success":true,"data":{"matchId":9984,
                %s
                "matchInfos":[
                %s
                ,%s
                ]}}
                """.formatted(
                matchTime == null ? "" : "\"matchTime\":\"" + matchTime + "\",",
                gameJson(1, 100, 200, bo1Start),
                gameJson(2, 100, 200, bo2Start));
    }

    private static String gameJson(int bo, long winTeamId, long loseTeamId, String startTime) {
        String timeField = startTime == null ? "" : "\"matchStartTime\":\"" + startTime + "\",";
        return "{\"bo\":" + bo + "," + timeField
                + "\"matchWin\":" + winTeamId + ",\"gameTime\":1800,\"teamInfos\":["
                + teamJson(winTeamId, bo)
                + "," + teamJson(loseTeamId, bo)
                + "]}";
    }

    private static String teamJson(long teamId, int bo) {
        StringBuilder players = new StringBuilder();
        int base = (int) (teamId == 100 ? 0 : 5);
        String[] positions = {"TOP", "JUN", "MID", "BOT", "SUP"};
        for (int i = 0; i < 5; i++) {
            long playerId = 1L + base + i + (bo - 1) * 10L;
            players.append(players.isEmpty() ? "" : ",").append("""
                    {"playerId":%d,"playerLocation":"%s","heroId":%d,
                     "battleDetail":{"kills":2,"death":1,"assist":1},
                     "damageDetail":{"heroDamage":26},
                     "otherDetail":{"golds":251,"firstBlood":false},
                     "visionDetail":{"wardPlaced":8,"wardKilled":2},"minionKilled":180}
                    """.formatted(playerId, positions[i], 1 + i));
        }
        return """
                {"teamId":%d,"kills":7,"golds":1000,"dragonAmount":2,"baronAmount":1,
                 "turretAmount":6,"playerInfos":[%s]}
                """.formatted(teamId, players);
    }
}
