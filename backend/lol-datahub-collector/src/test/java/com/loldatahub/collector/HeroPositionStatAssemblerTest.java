package com.loldatahub.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.source.model.HeroRecordSourceRecord;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.MatchPlayerGameSourceRecord;
import com.loldatahub.source.model.PlayerHeroRecordPayload;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeroPositionStatAssemblerTest {
    private static final List<String> POSITIONS = List.of("TOP", "JUN", "MID", "BOT", "SUP");

    @Test
    void sameChampionPlayedInTwoLanesProducesIndependentRows() {
        List<PlayerStatSourceRecord> players = new ArrayList<>();
        Map<Long, List<HeroRecordSourceRecord>> recordsByPlayer = new HashMap<>();
        List<HeroRecordSourceRecord> allRecords = new ArrayList<>();
        for (long playerId = 1; playerId <= 10; playerId++) {
            PlayerStatSourceRecord player = mock(PlayerStatSourceRecord.class);
            when(player.playerId()).thenReturn(playerId);
            when(player.playerName()).thenReturn("Player" + playerId);
            players.add(player);
            recordsByPlayer.put(playerId, new ArrayList<>());
        }

        for (int game = 1; game <= 2; game++) {
            for (long playerId = 1; playerId <= 10; playerId++) {
                String position = POSITIONS.get((int) ((playerId - 1) % 5));
                long heroId = game == 1 && playerId == 1
                        ? 50
                        : game == 2 && playerId == 3 ? 50 : 1000 + game * 10L + playerId;
                long teamId = playerId <= 5 ? 1 : 2;
                HeroRecordSourceRecord record = new HeroRecordSourceRecord(
                        heroId, "Hero" + heroId, null, 9000, game, position, true,
                        1, 0, 2, teamId, 1
                );
                recordsByPlayer.get(playerId).add(record);
                allRecords.add(record);
            }
        }

        List<PlayerHeroRecordPayload> payloads = recordsByPlayer.entrySet().stream()
                .map(entry -> new PlayerHeroRecordPayload(entry.getKey(), entry.getValue()))
                .toList();
        List<HeroStatSourceRecord> heroes = buildOfficialTotals(allRecords);
        HeroStagePayload stage = new HeroStagePayload(
                2, null, new ObjectMapper().createArrayNode(), heroes
        );

        HeroPositionStatAssembler.Result result = HeroPositionStatAssembler.assemble(stage, players, payloads);

        assertThat(result.positionsByChampion().get(50L)).containsExactly("TOP", "MID");
        assertThat(result.rows()).filteredOn(row -> row.championId() == 50L)
                .extracting(HeroPositionStatAssembler.PositionPlayerAggregate::position)
                .containsExactly("TOP", "MID");
        assertThat(result.rows()).filteredOn(row -> row.championId() == 50L)
                .allSatisfy(row -> assertThat(row.pickCount()).isEqualTo(1));
    }

    @Test
    void uniquelyRepairsOfficialZeroHeroAndMissingPlayerRecord() {
        List<PlayerStatSourceRecord> players = new ArrayList<>();
        Map<Long, List<HeroRecordSourceRecord>> recordsByPlayer = new HashMap<>();
        List<HeroRecordSourceRecord> expectedRecords = new ArrayList<>();
        List<MatchPlayerGameSourceRecord> secondGameDetails = new ArrayList<>();
        for (long playerId = 1; playerId <= 10; playerId++) {
            String position = POSITIONS.get((int) ((playerId - 1) % 5));
            PlayerStatSourceRecord player = mock(PlayerStatSourceRecord.class);
            when(player.playerId()).thenReturn(playerId);
            when(player.playerName()).thenReturn("Player" + playerId);
            when(player.playerLocation()).thenReturn(switch (position) {
                case "JUN" -> "JUG";
                case "BOT" -> "AD";
                default -> position;
            });
            players.add(player);
            recordsByPlayer.put(playerId, new ArrayList<>());

            long teamId = playerId <= 5 ? 1 : 2;
            HeroRecordSourceRecord firstGame = new HeroRecordSourceRecord(
                    100 + playerId, "Hero" + (100 + playerId), null, 9001, 1, "", true,
                    playerId, 1, 2, teamId, 1
            );
            expectedRecords.add(firstGame);
            recordsByPlayer.get(playerId).add(playerId == 1
                    ? new HeroRecordSourceRecord(
                            0, "", "", 9001, 1, "", true,
                            playerId, 1, 2, teamId, 1)
                    : firstGame);

            HeroRecordSourceRecord secondGame = new HeroRecordSourceRecord(
                    200 + playerId, "Hero" + (200 + playerId), null, 9002, 1, position, true,
                    playerId, 2, 3, teamId, 2
            );
            expectedRecords.add(secondGame);
            if (playerId != 2) {
                recordsByPlayer.get(playerId).add(secondGame);
            }
            secondGameDetails.add(new MatchPlayerGameSourceRecord(
                    9002, 1, playerId, position, 200 + playerId, "Hero" + (200 + playerId), "",
                    teamId, 2, playerId, 2, 3
            ));
        }

        List<PlayerHeroRecordPayload> payloads = recordsByPlayer.entrySet().stream()
                .map(entry -> new PlayerHeroRecordPayload(entry.getKey(), entry.getValue()))
                .toList();
        HeroStagePayload stage = new HeroStagePayload(
                2, null, new ObjectMapper().createArrayNode(), buildOfficialTotals(expectedRecords)
        );

        HeroPositionStatAssembler.Result result = HeroPositionStatAssembler.assemble(
                stage, players, payloads, secondGameDetails
        );

        assertThat(result.rows()).hasSize(20);
        assertThat(result.rows()).filteredOn(row -> row.championId() == 101L)
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.playerId()).isEqualTo(1L);
                    assertThat(row.position()).isEqualTo("TOP");
                });
        assertThat(result.rows()).filteredOn(row -> row.championId() == 202L)
                .singleElement()
                .satisfies(row -> assertThat(row.playerId()).isEqualTo(2L));
    }

    private List<HeroStatSourceRecord> buildOfficialTotals(List<HeroRecordSourceRecord> records) {
        Map<Long, List<HeroRecordSourceRecord>> byHero = new HashMap<>();
        records.forEach(record -> byHero.computeIfAbsent(record.heroId(), ignored -> new ArrayList<>()).add(record));
        return byHero.entrySet().stream().map(entry -> {
            long heroId = entry.getKey();
            List<HeroRecordSourceRecord> heroRecords = entry.getValue();
            HeroStatSourceRecord hero = mock(HeroStatSourceRecord.class);
            when(hero.heroId()).thenReturn(heroId);
            when(hero.pickCount()).thenReturn((long) heroRecords.size());
            when(hero.winningCount()).thenReturn(heroRecords.stream()
                    .filter(record -> record.teamId() == record.winTeamId()).count());
            when(hero.totalKills()).thenReturn(heroRecords.stream().mapToLong(HeroRecordSourceRecord::kill).sum());
            when(hero.totalDeath()).thenReturn(heroRecords.stream().mapToLong(HeroRecordSourceRecord::death).sum());
            when(hero.totalAssists()).thenReturn(heroRecords.stream().mapToLong(HeroRecordSourceRecord::assist).sum());
            return hero;
        }).toList();
    }
}
