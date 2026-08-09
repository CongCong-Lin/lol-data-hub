package com.loldatahub.collector;

import com.loldatahub.source.TjStatsSourceException;
import com.loldatahub.source.model.HeroRecordSourceRecord;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.PlayerHeroRecordPayload;
import com.loldatahub.source.model.PlayerStatSourceRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

final class HeroPositionStatAssembler {
    private static final List<String> POSITION_ORDER = List.of("TOP", "JUN", "MID", "BOT", "SUP");
    private static final Set<String> VALID_POSITIONS = Set.copyOf(POSITION_ORDER);

    private HeroPositionStatAssembler() {
    }

    static Result assemble(HeroStagePayload heroPayload,
                           List<PlayerStatSourceRecord> players,
                           List<PlayerHeroRecordPayload> playerRecords) {
        Map<Long, String> playerNames = playerNames(players);
        Map<Long, PlayerHeroRecordPayload> recordsByPlayer = new HashMap<>();
        for (PlayerHeroRecordPayload payload : playerRecords) {
            if (recordsByPlayer.put(payload.playerId(), payload) != null) {
                throw new TjStatsSourceException("HERO_POSITION: 选手英雄记录重复，playerId=" + payload.playerId());
            }
        }
        if (!recordsByPlayer.keySet().equals(playerNames.keySet())) {
            Set<Long> missing = new HashSet<>(playerNames.keySet());
            missing.removeAll(recordsByPlayer.keySet());
            Set<Long> unexpected = new HashSet<>(recordsByPlayer.keySet());
            unexpected.removeAll(playerNames.keySet());
            throw new TjStatsSourceException(
                    "HERO_POSITION: 选手与英雄记录不完整，缺失=" + missing + "，多余=" + unexpected);
        }

        Map<AggregateKey, MutableAggregate> aggregates = new HashMap<>();
        Map<GameKey, List<HeroRecordSourceRecord>> games = new HashMap<>();
        Set<GameSlotKey> occupiedSlots = new HashSet<>();
        Map<Long, MutableHeroTotal> actualHeroTotals = new HashMap<>();
        int recordCount = 0;

        for (Map.Entry<Long, PlayerHeroRecordPayload> entry : recordsByPlayer.entrySet()) {
            long playerId = entry.getKey();
            String playerName = playerNames.get(playerId);
            for (HeroRecordSourceRecord record : entry.getValue().records()) {
                recordCount++;
                String position = normalizePosition(record.role());
                GameKey gameKey = new GameKey(record.matchId(), record.bo());
                games.computeIfAbsent(gameKey, ignored -> new ArrayList<>()).add(record);
                GameSlotKey slotKey = new GameSlotKey(
                        record.matchId(), record.bo(), record.teamId(), position
                );
                if (!occupiedSlots.add(slotKey)) {
                    throw new TjStatsSourceException(
                            "HERO_POSITION: 同一对局战队分路存在重复选手，matchId=" + record.matchId()
                                    + "，bo=" + record.bo() + "，teamId=" + record.teamId()
                                    + "，position=" + position);
                }

                AggregateKey key = new AggregateKey(record.heroId(), position, playerId, playerName);
                aggregates.computeIfAbsent(key, ignored -> new MutableAggregate())
                        .add(record, record.teamId() == record.winTeamId());
                actualHeroTotals.computeIfAbsent(record.heroId(), ignored -> new MutableHeroTotal())
                        .add(record, record.teamId() == record.winTeamId());
            }
        }

        long expectedRecordCount;
        try {
            expectedRecordCount = Math.multiplyExact(heroPayload.sampleBaseCount(), 10L);
        } catch (ArithmeticException exception) {
            throw new TjStatsSourceException("HERO_POSITION: 样本基数过大，无法校验逐局记录", exception);
        }
        if (recordCount != expectedRecordCount) {
            throw new TjStatsSourceException(
                    "HERO_POSITION: 逐局记录不完整，期望 " + expectedRecordCount + " 条，实际 " + recordCount + " 条");
        }
        if (games.size() != heroPayload.sampleBaseCount()) {
            throw new TjStatsSourceException(
                    "HERO_POSITION: 对局数量与样本基数不一致，期望 " + heroPayload.sampleBaseCount()
                            + "，实际 " + games.size());
        }
        validateGames(games);
        validateHeroTotals(heroPayload.heroes(), actualHeroTotals);

        List<PositionPlayerAggregate> rows = aggregates.entrySet().stream()
                .map(entry -> entry.getValue().toRecord(entry.getKey()))
                .sorted(Comparator.comparingLong(PositionPlayerAggregate::championId)
                        .thenComparingInt(row -> POSITION_ORDER.indexOf(row.position()))
                        .thenComparingLong(PositionPlayerAggregate::playerId))
                .toList();

        Map<Long, List<String>> positionsByChampion = new LinkedHashMap<>();
        for (PositionPlayerAggregate row : rows) {
            positionsByChampion.computeIfAbsent(row.championId(), ignored -> new ArrayList<>());
            List<String> positions = positionsByChampion.get(row.championId());
            if (!positions.contains(row.position())) {
                positions.add(row.position());
            }
        }
        positionsByChampion.replaceAll((ignored, positions) -> List.copyOf(positions));
        return new Result(rows, Map.copyOf(positionsByChampion));
    }

    private static Map<Long, String> playerNames(List<PlayerStatSourceRecord> players) {
        Map<Long, String> result = new HashMap<>();
        for (PlayerStatSourceRecord player : players) {
            if (player.playerId() == null || player.playerId() <= 0) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 无法为缺少 playerId 的选手采集逐局英雄数据：" + player.playerName());
            }
            String previous = result.put(player.playerId(), player.playerName().trim());
            if (previous != null) {
                throw new TjStatsSourceException("HERO_POSITION: playerId 重复：" + player.playerId());
            }
        }
        return result;
    }

    private static String normalizePosition(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_POSITIONS.contains(normalized)) {
            throw new TjStatsSourceException("HERO_POSITION: 未知分路：" + value);
        }
        return normalized;
    }

    private static void validateGames(Map<GameKey, List<HeroRecordSourceRecord>> games) {
        for (Map.Entry<GameKey, List<HeroRecordSourceRecord>> entry : games.entrySet()) {
            GameKey key = entry.getKey();
            List<HeroRecordSourceRecord> records = entry.getValue();
            if (records.size() != 10) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 单局记录数必须为 10，matchId=" + key.matchId()
                                + "，bo=" + key.bo() + "，实际 " + records.size());
            }
            Map<Long, Integer> teamCounts = new HashMap<>();
            Map<String, Integer> positionCounts = new HashMap<>();
            Set<Long> winTeamIds = new HashSet<>();
            for (HeroRecordSourceRecord record : records) {
                teamCounts.merge(record.teamId(), 1, Integer::sum);
                positionCounts.merge(normalizePosition(record.role()), 1, Integer::sum);
                winTeamIds.add(record.winTeamId());
            }
            if (teamCounts.size() != 2 || teamCounts.values().stream().anyMatch(count -> count != 5)) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 单局必须由两支各 5 人的战队组成，matchId=" + key.matchId() + "，bo=" + key.bo());
            }
            if (!positionCounts.keySet().equals(VALID_POSITIONS)
                    || positionCounts.values().stream().anyMatch(count -> count != 2)) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 单局五个分路必须各出现 2 次，matchId=" + key.matchId() + "，bo=" + key.bo());
            }
            if (winTeamIds.size() != 1 || !teamCounts.containsKey(winTeamIds.iterator().next())) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 单局胜方战队无效，matchId=" + key.matchId() + "，bo=" + key.bo());
            }
        }
    }

    private static void validateHeroTotals(List<HeroStatSourceRecord> heroes,
                                           Map<Long, MutableHeroTotal> actualTotals) {
        Set<Long> officialIds = new HashSet<>();
        for (HeroStatSourceRecord hero : heroes) {
            officialIds.add(hero.heroId());
            MutableHeroTotal actual = actualTotals.getOrDefault(hero.heroId(), new MutableHeroTotal());
            if (actual.pickCount != hero.pickCount()
                    || actual.winningCount != hero.winningCount()
                    || actual.totalKills != hero.totalKills()
                    || actual.totalDeaths != hero.totalDeath()
                    || actual.totalAssists != hero.totalAssists()) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 英雄逐局合计与官网聚合不一致，heroId=" + hero.heroId()
                                + "，官网=" + hero.pickCount() + "/" + hero.winningCount() + "/"
                                + hero.totalKills() + "/" + hero.totalDeath() + "/" + hero.totalAssists()
                                + "，逐局=" + actual.pickCount + "/" + actual.winningCount + "/"
                                + actual.totalKills + "/" + actual.totalDeaths + "/" + actual.totalAssists);
            }
        }
        Set<Long> unexpected = new HashSet<>(actualTotals.keySet());
        unexpected.removeAll(officialIds);
        if (!unexpected.isEmpty()) {
            throw new TjStatsSourceException("HERO_POSITION: 逐局记录包含官网聚合中不存在的英雄：" + unexpected);
        }
    }

    record Result(
            List<PositionPlayerAggregate> rows,
            Map<Long, List<String>> positionsByChampion
    ) {
    }

    record PositionPlayerAggregate(
            long championId,
            String position,
            long playerId,
            String playerName,
            long pickCount,
            long winningCount,
            long totalKills,
            long totalDeaths,
            long totalAssists
    ) {
    }

    private record AggregateKey(long championId, String position, long playerId, String playerName) {
    }

    private record GameKey(long matchId, long bo) {
    }

    private record GameSlotKey(long matchId, long bo, long teamId, String position) {
    }

    private static final class MutableAggregate {
        private long pickCount;
        private long winningCount;
        private long totalKills;
        private long totalDeaths;
        private long totalAssists;

        void add(HeroRecordSourceRecord record, boolean won) {
            pickCount++;
            if (won) {
                winningCount++;
            }
            totalKills += record.kill();
            totalDeaths += record.death();
            totalAssists += record.assist();
        }

        PositionPlayerAggregate toRecord(AggregateKey key) {
            return new PositionPlayerAggregate(
                    key.championId(), key.position(), key.playerId(), key.playerName(),
                    pickCount, winningCount, totalKills, totalDeaths, totalAssists
            );
        }
    }

    private static final class MutableHeroTotal {
        private long pickCount;
        private long winningCount;
        private long totalKills;
        private long totalDeaths;
        private long totalAssists;

        void add(HeroRecordSourceRecord record, boolean won) {
            pickCount++;
            if (won) {
                winningCount++;
            }
            totalKills += record.kill();
            totalDeaths += record.death();
            totalAssists += record.assist();
        }
    }
}
