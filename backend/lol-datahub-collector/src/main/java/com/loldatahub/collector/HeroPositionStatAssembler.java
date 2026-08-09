package com.loldatahub.collector;

import com.loldatahub.source.TjStatsSourceException;
import com.loldatahub.source.model.HeroRecordSourceRecord;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.HeroStatSourceRecord;
import com.loldatahub.source.model.MatchPlayerGameSourceRecord;
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
        return assemble(heroPayload, players, playerRecords, List.of());
    }

    static Result assemble(HeroStagePayload heroPayload,
                           List<PlayerStatSourceRecord> players,
                           List<PlayerHeroRecordPayload> playerRecords,
                           List<MatchPlayerGameSourceRecord> matchGameRecords) {
        PlayerDirectory playerDirectory = playerDirectory(players);
        Map<Long, String> playerNames = playerDirectory.names();
        Map<MatchPlayerKey, MatchPlayerGameSourceRecord> matchDetailsByPlayer = new HashMap<>();
        for (MatchPlayerGameSourceRecord row : matchGameRecords) {
            MatchPlayerKey key = new MatchPlayerKey(row.matchId(), row.bo(), row.playerId());
            if (matchDetailsByPlayer.put(key, row) != null) {
                throw new TjStatsSourceException("HERO_POSITION: 比赛详情中的选手分路重复：" + key);
            }
        }
        Map<Long, PlayerHeroRecordPayload> payloadsByPlayer = new HashMap<>();
        for (PlayerHeroRecordPayload payload : playerRecords) {
            if (payloadsByPlayer.put(payload.playerId(), payload) != null) {
                throw new TjStatsSourceException("HERO_POSITION: 选手英雄记录重复，playerId=" + payload.playerId());
            }
        }
        if (!payloadsByPlayer.keySet().equals(playerNames.keySet())) {
            Set<Long> missing = new HashSet<>(playerNames.keySet());
            missing.removeAll(payloadsByPlayer.keySet());
            Set<Long> unexpected = new HashSet<>(payloadsByPlayer.keySet());
            unexpected.removeAll(playerNames.keySet());
            throw new TjStatsSourceException(
                    "HERO_POSITION: 选手与英雄记录不完整，缺失=" + missing + "，多余=" + unexpected);
        }

        Map<MatchPlayerKey, HeroRecordSourceRecord> recordsByGamePlayer = new LinkedHashMap<>();
        for (PlayerHeroRecordPayload payload : payloadsByPlayer.values()) {
            for (HeroRecordSourceRecord record : payload.records()) {
                MatchPlayerKey key = new MatchPlayerKey(record.matchId(), record.bo(), payload.playerId());
                if (recordsByGamePlayer.put(key, record) != null) {
                    throw new TjStatsSourceException("HERO_POSITION: 同一选手存在重复对局记录 " + key);
                }
            }
        }
        mergeMatchDetails(recordsByGamePlayer, matchDetailsByPlayer, playerNames);
        repairUnknownHeroIds(heroPayload, recordsByGamePlayer);

        Map<AggregateKey, MutableAggregate> aggregates = new HashMap<>();
        Map<GameKey, List<PositionedRecord>> games = new HashMap<>();
        Set<GameSlotKey> occupiedSlots = new HashSet<>();
        Map<Long, MutableHeroTotal> actualHeroTotals = new HashMap<>();
        int recordCount = 0;

        for (Map.Entry<MatchPlayerKey, HeroRecordSourceRecord> entry : recordsByGamePlayer.entrySet()) {
            MatchPlayerKey playerKey = entry.getKey();
            long playerId = playerKey.playerId();
            String playerName = playerNames.get(playerId);
            HeroRecordSourceRecord record = entry.getValue();
            recordCount++;
            MatchPlayerGameSourceRecord detail = matchDetailsByPlayer.get(playerKey);
            String position = resolvePosition(
                    record.role(), detail == null ? null : detail.position(),
                    playerDirectory.positions().get(playerId), record.matchId(), record.bo(), playerId
            );
            GameKey gameKey = new GameKey(record.matchId(), record.bo());
            games.computeIfAbsent(gameKey, ignored -> new ArrayList<>())
                    .add(new PositionedRecord(record, position));
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

    private static PlayerDirectory playerDirectory(List<PlayerStatSourceRecord> players) {
        Map<Long, String> names = new HashMap<>();
        Map<Long, String> positions = new HashMap<>();
        for (PlayerStatSourceRecord player : players) {
            if (player.playerId() == null || player.playerId() <= 0) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 无法为缺少 playerId 的选手采集逐局英雄数据：" + player.playerName());
            }
            String previous = names.put(player.playerId(), player.playerName().trim());
            if (previous != null) {
                throw new TjStatsSourceException("HERO_POSITION: playerId 重复：" + player.playerId());
            }
            positions.put(player.playerId(), normalizePlayerPosition(player.playerLocation()));
        }
        return new PlayerDirectory(Map.copyOf(names), Map.copyOf(positions));
    }

    private static String normalizePlayerPosition(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "JUG" -> "JUN";
            case "AD" -> "BOT";
            default -> VALID_POSITIONS.contains(normalized) ? normalized : "";
        };
    }

    private static void mergeMatchDetails(
            Map<MatchPlayerKey, HeroRecordSourceRecord> recordsByGamePlayer,
            Map<MatchPlayerKey, MatchPlayerGameSourceRecord> matchDetailsByPlayer,
            Map<Long, String> playerNames) {
        for (Map.Entry<MatchPlayerKey, MatchPlayerGameSourceRecord> entry : matchDetailsByPlayer.entrySet()) {
            MatchPlayerKey key = entry.getKey();
            MatchPlayerGameSourceRecord detail = entry.getValue();
            if (!playerNames.containsKey(key.playerId())) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 比赛详情包含赛事选手列表之外的 playerId=" + key.playerId());
            }
            HeroRecordSourceRecord existing = recordsByGamePlayer.get(key);
            if (existing == null) {
                recordsByGamePlayer.put(key, fromMatchDetail(detail));
                continue;
            }
            if (existing.heroId() > 0 && existing.heroId() != detail.heroId()) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 逐局记录与比赛详情的英雄 ID 不一致，matchId=" + key.matchId()
                                + "，bo=" + key.bo() + "，playerId=" + key.playerId());
            }
            if (existing.heroId() == 0) {
                if (existing.teamId() != detail.teamId()
                        || existing.winTeamId() != detail.winTeamId()
                        || existing.kill() != detail.kill()
                        || existing.death() != detail.death()
                        || existing.assist() != detail.assist()) {
                    throw new TjStatsSourceException(
                            "HERO_POSITION: 官网占位记录无法与比赛详情一致对账，matchId=" + key.matchId()
                                    + "，bo=" + key.bo() + "，playerId=" + key.playerId());
                }
                recordsByGamePlayer.put(key, fromMatchDetail(detail));
            }
        }
    }

    private static HeroRecordSourceRecord fromMatchDetail(MatchPlayerGameSourceRecord detail) {
        return new HeroRecordSourceRecord(
                detail.heroId(), detail.heroName(), detail.heroTitle(), detail.matchId(), detail.bo(),
                detail.position(), true, detail.kill(), detail.death(), detail.assist(),
                detail.teamId(), detail.winTeamId()
        );
    }

    private static void repairUnknownHeroIds(
            HeroStagePayload heroPayload,
            Map<MatchPlayerKey, HeroRecordSourceRecord> recordsByGamePlayer) {
        if (recordsByGamePlayer.values().stream().noneMatch(record -> record.heroId() == 0)) {
            return;
        }
        Map<Long, HeroStatSourceRecord> officialHeroes = new LinkedHashMap<>();
        Map<Long, MutableHeroResidual> residuals = new LinkedHashMap<>();
        for (HeroStatSourceRecord hero : heroPayload.heroes()) {
            if (officialHeroes.put(hero.heroId(), hero) != null) {
                throw new TjStatsSourceException("HERO_POSITION: 官网聚合英雄 ID 重复：" + hero.heroId());
            }
            residuals.put(hero.heroId(), new MutableHeroResidual(hero));
        }

        List<MatchPlayerKey> unknownKeys = new ArrayList<>();
        for (Map.Entry<MatchPlayerKey, HeroRecordSourceRecord> entry : recordsByGamePlayer.entrySet()) {
            HeroRecordSourceRecord record = entry.getValue();
            if (record.heroId() == 0) {
                unknownKeys.add(entry.getKey());
                continue;
            }
            MutableHeroResidual residual = residuals.get(record.heroId());
            if (residual == null || !residual.subtract(record)) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 逐局数据无法与官网英雄聚合对账，heroId=" + record.heroId());
            }
        }
        unknownKeys.sort(Comparator.comparingLong(MatchPlayerKey::matchId)
                .thenComparingLong(MatchPlayerKey::bo)
                .thenComparingLong(MatchPlayerKey::playerId));

        while (!unknownKeys.isEmpty()) {
            boolean repaired = false;
            for (int index = 0; index < unknownKeys.size(); index++) {
                MatchPlayerKey key = unknownKeys.get(index);
                HeroRecordSourceRecord unknown = recordsByGamePlayer.get(key);
                List<Long> candidates = residuals.entrySet().stream()
                        .filter(entry -> entry.getValue().matchesExactly(unknown))
                        .map(Map.Entry::getKey)
                        .toList();
                if (candidates.size() != 1) {
                    continue;
                }
                long heroId = candidates.getFirst();
                HeroStatSourceRecord hero = officialHeroes.get(heroId);
                recordsByGamePlayer.put(key, withRecoveredHero(unknown, hero));
                residuals.get(heroId).subtract(unknown);
                unknownKeys.remove(index);
                repaired = true;
                break;
            }
            if (!repaired) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 官网占位英雄无法通过聚合差额唯一恢复，对局=" + unknownKeys);
            }
        }
    }

    private static HeroRecordSourceRecord withRecoveredHero(
            HeroRecordSourceRecord record,
            HeroStatSourceRecord hero) {
        String heroName = hero.heroName() == null || hero.heroName().isBlank()
                ? hero.heroCnName()
                : hero.heroName();
        return new HeroRecordSourceRecord(
                hero.heroId(), heroName, hero.heroCnTitle(), record.matchId(), record.bo(), record.role(),
                record.isRole(), record.kill(), record.death(), record.assist(),
                record.teamId(), record.winTeamId()
        );
    }

    private static String resolvePosition(String actualRole,
                                          String matchDetailPosition,
                                          String playerPosition,
                                          long matchId,
                                          long bo,
                                          long playerId) {
        String value = actualRole;
        if (value == null || value.isBlank()) {
            value = matchDetailPosition;
        }
        if (value == null || value.isBlank()) {
            value = playerPosition;
        }
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!VALID_POSITIONS.contains(normalized)) {
            throw new TjStatsSourceException(
                    "HERO_POSITION: 无法从逐局记录、比赛详情或赛段选手位置确定分路，matchId=" + matchId
                            + "，bo=" + bo + "，playerId=" + playerId);
        }
        return normalized;
    }

    private static void validateGames(Map<GameKey, List<PositionedRecord>> games) {
        for (Map.Entry<GameKey, List<PositionedRecord>> entry : games.entrySet()) {
            GameKey key = entry.getKey();
            List<PositionedRecord> records = entry.getValue();
            if (records.size() != 10) {
                throw new TjStatsSourceException(
                        "HERO_POSITION: 单局记录数必须为 10，matchId=" + key.matchId()
                                + "，bo=" + key.bo() + "，实际 " + records.size());
            }
            Map<Long, Integer> teamCounts = new HashMap<>();
            Map<String, Integer> positionCounts = new HashMap<>();
            Set<Long> winTeamIds = new HashSet<>();
            for (PositionedRecord positioned : records) {
                HeroRecordSourceRecord record = positioned.record();
                teamCounts.merge(record.teamId(), 1, Integer::sum);
                positionCounts.merge(positioned.position(), 1, Integer::sum);
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

    private record PositionedRecord(HeroRecordSourceRecord record, String position) {
    }

    private record MatchPlayerKey(long matchId, long bo, long playerId) {
    }

    private record PlayerDirectory(Map<Long, String> names, Map<Long, String> positions) {
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

    private static final class MutableHeroResidual {
        private long pickCount;
        private long winningCount;
        private long totalKills;
        private long totalDeaths;
        private long totalAssists;

        private MutableHeroResidual(HeroStatSourceRecord hero) {
            pickCount = hero.pickCount();
            winningCount = hero.winningCount();
            totalKills = hero.totalKills();
            totalDeaths = hero.totalDeath();
            totalAssists = hero.totalAssists();
        }

        private boolean subtract(HeroRecordSourceRecord record) {
            long wins = record.teamId() == record.winTeamId() ? 1 : 0;
            if (pickCount < 1 || winningCount < wins
                    || totalKills < record.kill() || totalDeaths < record.death()
                    || totalAssists < record.assist()) {
                return false;
            }
            pickCount--;
            winningCount -= wins;
            totalKills -= record.kill();
            totalDeaths -= record.death();
            totalAssists -= record.assist();
            return true;
        }

        private boolean matchesExactly(HeroRecordSourceRecord record) {
            long wins = record.teamId() == record.winTeamId() ? 1 : 0;
            return pickCount == 1
                    && winningCount == wins
                    && totalKills == record.kill()
                    && totalDeaths == record.death()
                    && totalAssists == record.assist();
        }
    }
}
