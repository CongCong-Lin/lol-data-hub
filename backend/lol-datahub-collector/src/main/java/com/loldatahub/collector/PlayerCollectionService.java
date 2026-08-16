package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerIdentity;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatWriteMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamStageDetailMetricWriteMapper;
import com.loldatahub.infrastructure.model.PlayerStageStatWrite;
import com.loldatahub.infrastructure.model.PlayerWrite;
import com.loldatahub.infrastructure.model.TeamStageDetailMetricWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import com.loldatahub.source.model.HeroRecordSourceRecord;
import com.loldatahub.source.model.MatchPlayerMetricSourceRecord;
import com.loldatahub.source.model.MatchTeamMetricSourceRecord;
import com.loldatahub.source.model.PlayerHeroRecordPayload;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Service
public class PlayerCollectionService {
    static final String CONTENT_SCHEMA_VERSION = "player-v6-team-extended-metrics";
    private static final Logger log = LoggerFactory.getLogger(PlayerCollectionService.class);

    private final TjStatsClient client;
    private final TjStatsResponseParser parser;
    private final ObjectMapper objectMapper;
    private final CollectionMapper collectionMapper;
    private final PlayerStatWriteMapper writeMapper;
    private final TeamStageDetailMetricWriteMapper teamDetailMetricWriteMapper;
    private final PlayerStatisticsMapper statisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final CatalogCollectionService catalogCollectionService;
    private final TransactionTemplate transactionTemplate;

    public PlayerCollectionService(TjStatsClient client,
                                   TjStatsResponseParser parser,
                                   ObjectMapper objectMapper,
                                   CollectionMapper collectionMapper,
                                   PlayerStatWriteMapper writeMapper,
                                   TeamStageDetailMetricWriteMapper teamDetailMetricWriteMapper,
                                   PlayerStatisticsMapper statisticsMapper,
                                   SystemStateMapper systemStateMapper,
                                   CatalogCollectionService catalogCollectionService,
                                   TransactionTemplate transactionTemplate) {
        this.client = client;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.collectionMapper = collectionMapper;
        this.writeMapper = writeMapper;
        this.teamDetailMetricWriteMapper = teamDetailMetricWriteMapper;
        this.statisticsMapper = statisticsMapper;
        this.systemStateMapper = systemStateMapper;
        this.catalogCollectionService = catalogCollectionService;
        this.transactionTemplate = transactionTemplate;
    }

    public synchronized CollectionResult collect(long seasonId, List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个赛段");
        }
        List<Long> normalizedStageIds = stageIds.stream().distinct().sorted().toList();

        CollectionMapper.GeneratedId holder = new CollectionMapper.GeneratedId();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        collectionMapper.insertRun("PLAYER", seasonId, toJson(normalizedStageIds), startedAt, holder);
        long runId = holder.getId();

        try {
            catalogCollectionService.sync(seasonId);
            List<Long> unchanged = new ArrayList<>();
            List<PlayerStageCandidate> changedStages = new ArrayList<>();

            for (Long stageId : normalizedStageIds) {
                String rawJson = client.fetchPlayerStatistics(seasonId, stageId);
                String rawHash = sha256(rawJson);
                OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
                collectionMapper.insertRawResponse(
                        runId,
                        "/compound/public/player",
                        toJson(Map.of("seasonId", seasonId, "stageIds", stageId)),
                        rawJson,
                        rawHash,
                        collectedAt
                );

                List<PlayerStatSourceRecord> players = parser.parsePlayerStage(rawJson);
                StringBuilder hashMaterial = new StringBuilder(CONTENT_SCHEMA_VERSION).append('\n');
                appendHashMaterial(hashMaterial, "/compound/public/player", rawJson);
                ExactStageData exactData = enrichExactRates(
                        runId, seasonId, stageId, players, collectedAt, hashMaterial);
                String contentHash = sha256(hashMaterial.toString());

                if (contentHash.equals(statisticsMapper.findCurrentContentHash(seasonId, stageId))) {
                    unchanged.add(stageId);
                    continue;
                }
                changedStages.add(new PlayerStageCandidate(
                        stageId, contentHash, collectedAt, players, exactData));
            }

            if (changedStages.isEmpty()) {
                collectionMapper.finishRun(runId, "NO_CHANGE", OffsetDateTime.now(ZoneOffset.UTC), 0, null);
                return new CollectionResult(runId, "NO_CHANGE", 0, unchanged);
            }

            int changedRecords = changedStages.stream()
                    .mapToInt(candidate -> 1 + candidate.players().size()
                            + candidate.exactData().teamMetrics().size()).sum();
            Integer committedRecords = transactionTemplate.execute(status -> {
                for (PlayerStageCandidate candidate : changedStages) {
                    long stageId = candidate.stageId();
                    writeMapper.upsertCollectionCurrent(
                            seasonId, stageId, candidate.contentHash(), candidate.collectedAt(), runId);
                    writeMapper.deleteCurrentForStage(seasonId, stageId);
                    for (PlayerStatSourceRecord player : candidate.players()) {
                        String playerKey = PlayerIdentity.resolve(player.playerId(), player.playerName());
                        writeMapper.upsertPlayer(new PlayerWrite(
                                playerKey, player.playerId(), player.playerName(), player.playerAvatar()));
                        PlayerStageStatWrite stat = new PlayerStageStatWrite(
                                runId, seasonId, stageId, playerKey,
                                player.teamName(), player.teamLogo(), player.playerLocation(),
                                player.matchCount(), player.boCount(), player.mvpCount(), player.mvpVotes(),
                                player.totalKills(), player.totalAssists(), player.totalDeath(),
                                player.goldPerGame(), player.creepScorePerGame(),
                                player.wardPlacedPerGame(), player.wardKilledPerGame(),
                                exactRate(candidate.exactData().playerRates(), player.playerId(),
                                ExactPlayerRates::killParticipantPercent, player.killParticipantPercent()),
                                player.goldGapPerGame(),
                                exactRate(candidate.exactData().playerRates(), player.playerId(),
                                        ExactPlayerRates::damagePerGame, player.damagePerGame()),
                                exactRate(candidate.exactData().playerRates(), player.playerId(),
                                        ExactPlayerRates::damagePercent, player.damagePercent()),
                                exactRate(candidate.exactData().playerRates(), player.playerId(),
                                        ExactPlayerRates::goldPercent, player.goldPercent()),
                                candidate.collectedAt());
                        writeMapper.upsertCurrent(stat);
                        writeMapper.insertSnapshot(stat);
                    }
                    boolean keepExistingTeamMetrics = candidate.exactData().fallback()
                            && teamDetailMetricWriteMapper.countCurrentForStage(seasonId, stageId) > 0;
                    if (keepExistingTeamMetrics) {
                        // 精确指标计算失败时不能用空集覆盖已发布的战队明细指标；
                        // 选手精确比例已回退官网聚合值，战队指标保留上一次成功采集的结果。
                        log.warn("PLAYER {}:{} 精确指标计算失败，保留上一次的战队明细指标", seasonId, stageId);
                    } else {
                        teamDetailMetricWriteMapper.deleteCurrentForStage(seasonId, stageId);
                        for (TeamStageDetailMetric metric : candidate.exactData().teamMetrics()) {
                            TeamStageDetailMetricWrite write = new TeamStageDetailMetricWrite(
                                    runId, seasonId, stageId, metric.teamId(), metric.gameCount(),
                                    metric.totalAssists(), metric.totalDamage(), metric.totalGameSeconds(), metric.totalGold(),
                                    metric.totalWardsPlaced(), metric.totalWardsKilled(), metric.totalMinionKills(),
                                    metric.totalDragons(), metric.totalDragonOpportunities(), metric.totalBarons(),
                                    metric.totalBaronOpportunities(), metric.totalTurrets(), metric.totalTurretsLost(),
                                    metric.firstBloodGames(), candidate.collectedAt());
                            teamDetailMetricWriteMapper.upsertCurrent(write);
                            teamDetailMetricWriteMapper.insertSnapshot(write);
                        }
                    }
                }
                systemStateMapper.incrementDataVersion();
                collectionMapper.finishRun(
                        runId, "SUCCESS", OffsetDateTime.now(ZoneOffset.UTC), changedRecords, null);
                return changedRecords;
            });
            if (committedRecords == null) {
                throw new IllegalStateException("采集事务未返回提交结果");
            }
            return new CollectionResult(runId, "SUCCESS", committedRecords, unchanged);
        } catch (RuntimeException exception) {
            markFailed(runId, exception);
            throw exception;
        }
    }

    /**
     * 使用逐局英雄记录确定统计范围，再与比赛详情按比赛、局次、选手精确对齐。
     * 本地逐局记录过期时只刷新对应选手，比赛详情缺失时只补拉缺失比赛。
     */
    private ExactStageData enrichExactRates(long runId,
                                                         long seasonId,
                                                         long stageId,
                                                         List<PlayerStatSourceRecord> players,
                                                         OffsetDateTime collectedAt,
                                                         StringBuilder hashMaterial) {
        try {
            StringBuilder exactHashMaterial = new StringBuilder();
            Map<Long, String> detailsByMatch = loadExistingMatchDetails(seasonId, stageId);
            Map<Long, PlayerHeroRecordPayload> recordsByPlayer = new LinkedHashMap<>();
            Set<Long> requiredMatchIds = new TreeSet<>();

            for (PlayerStatSourceRecord player : players) {
                long playerId = requirePlayerId(player, stageId);
                String rawRecord = collectionMapper.findPlayerHeroRecordResponse(
                        seasonId, stageId, playerId);
                PlayerHeroRecordPayload payload = parseCompleteHeroRecords(rawRecord, player);
                if (payload == null) {
                    rawRecord = client.fetchPlayerHeroRecords(playerId, seasonId, stageId);
                    storeRawResponse(runId, "/compound/heroRecord", seasonId, stageId,
                            playerId, rawRecord, collectedAt);
                    payload = parser.parsePlayerHeroRecords(rawRecord, playerId);
                    if (!matchesOfficialTotals(payload, player)) {
                        throw new TjStatsSourceException(
                                "PLAYER_EXACT: 官网逐局记录与选手聚合总数不一致，playerId=" + playerId);
                    }
                }
                recordsByPlayer.put(playerId, payload);
                payload.records().stream().map(HeroRecordSourceRecord::matchId)
                        .forEach(requiredMatchIds::add);
                appendHashMaterial(exactHashMaterial, "/compound/heroRecord:" + playerId, rawRecord);
            }

            Map<GamePlayerKey, MatchPlayerMetricSourceRecord> metricsByGamePlayer = new HashMap<>();
            Map<GameTeamKey, MatchTeamMetricSourceRecord> teamMetricsByGame = new HashMap<>();
            boolean extendedTeamMetricsAvailable = true;
            Map<Long, String> usedDetailsByMatch = new LinkedHashMap<>();
            for (Long matchId : requiredMatchIds) {
                String rawDetail = detailsByMatch.get(matchId);
                boolean reusedLocalDetail = rawDetail != null;
                if (rawDetail == null) {
                    rawDetail = client.fetchMatchDetail(matchId);
                    storeMatchDetailRawResponse(
                            runId, seasonId, stageId, matchId, rawDetail, collectedAt);
                }
                List<MatchPlayerMetricSourceRecord> parsedMetrics;
                try {
                    parsedMetrics = parser.parseMatchPlayerMetrics(rawDetail, matchId);
                } catch (RuntimeException exception) {
                    if (!reusedLocalDetail) {
                        throw exception;
                    }
                    rawDetail = client.fetchMatchDetail(matchId);
                    storeMatchDetailRawResponse(
                            runId, seasonId, stageId, matchId, rawDetail, collectedAt);
                    parsedMetrics = parser.parseMatchPlayerMetrics(rawDetail, matchId);
                }
                usedDetailsByMatch.put(matchId, rawDetail);
                addMatchMetrics(metricsByGamePlayer, parsedMetrics);
                try {
                    addMatchTeamMetrics(teamMetricsByGame, parser.parseMatchTeamMetrics(rawDetail, matchId));
                } catch (RuntimeException exception) {
                    extendedTeamMetricsAvailable = false;
                    log.info("PLAYER {}:{} 的单局扩展战队指标不可用：{}", seasonId, stageId, exception.getMessage());
                }
            }

            // 官网会在比赛结束后分批更新同一个 matchDetail。旧响应可能是合法 JSON，
            // 但只包含前两局；发现逐局键缺失时刷新对应比赛，而不是让整个赛段回退。
            Set<Long> incompleteMatchIds = new TreeSet<>();
            for (PlayerHeroRecordPayload payload : recordsByPlayer.values()) {
                for (HeroRecordSourceRecord record : payload.records()) {
                    if (!metricsByGamePlayer.containsKey(
                            new GamePlayerKey(record.matchId(), record.bo(), payload.playerId()))) {
                        incompleteMatchIds.add(record.matchId());
                    }
                }
            }
            for (Long matchId : incompleteMatchIds) {
                String rawDetail = client.fetchMatchDetail(matchId);
                storeMatchDetailRawResponse(
                        runId, seasonId, stageId, matchId, rawDetail, collectedAt);
                metricsByGamePlayer.keySet().removeIf(key -> key.matchId() == matchId);
                teamMetricsByGame.keySet().removeIf(key -> key.matchId() == matchId);
                addMatchMetrics(
                        metricsByGamePlayer, parser.parseMatchPlayerMetrics(rawDetail, matchId));
                try {
                    addMatchTeamMetrics(teamMetricsByGame, parser.parseMatchTeamMetrics(rawDetail, matchId));
                } catch (RuntimeException exception) {
                    extendedTeamMetricsAvailable = false;
                }
                usedDetailsByMatch.put(matchId, rawDetail);
            }
            for (Map.Entry<Long, String> detail : usedDetailsByMatch.entrySet()) {
                appendHashMaterial(exactHashMaterial,
                        "/compound/matchDetail:" + detail.getKey(), detail.getValue());
            }

            Map<Long, ExactPlayerRates> result = new HashMap<>();
            for (PlayerStatSourceRecord player : players) {
                long playerId = requirePlayerId(player, stageId);
                PlayerHeroRecordPayload payload = recordsByPlayer.get(playerId);
                List<MatchPlayerMetricSourceRecord> metrics = new ArrayList<>();
                for (HeroRecordSourceRecord record : payload.records()) {
                    MatchPlayerMetricSourceRecord metric = metricsByGamePlayer.get(
                            new GamePlayerKey(record.matchId(), record.bo(), playerId));
                    if (metric == null) {
                        throw new TjStatsSourceException(
                                "PLAYER_EXACT: 缺少比赛详情，playerId=" + playerId
                                        + "，matchId=" + record.matchId() + "，bo=" + record.bo());
                    }
                    if (metric.kills() != record.kill()
                            || metric.assists() != record.assist()
                            || metric.deaths() != record.death()) {
                        throw new TjStatsSourceException(
                                "PLAYER_EXACT: 英雄逐局记录与比赛详情不一致，playerId=" + playerId
                                        + "，matchId=" + record.matchId() + "，bo=" + record.bo());
                    }
                    metrics.add(metric);
                }
                result.put(playerId, aggregateExactRates(metrics));
            }
            hashMaterial.append(exactHashMaterial);
            return new ExactStageData(
                    Map.copyOf(result),
                    aggregateTeamMetrics(metricsByGamePlayer.values(), teamMetricsByGame,
                            extendedTeamMetricsAvailable),
                    false);
        } catch (RuntimeException exception) {
            log.warn("PLAYER {}:{} 无法从本地比赛详情计算精确指标，回退官网聚合比例: {}",
                    seasonId, stageId, exception.getMessage());
            appendHashMaterial(hashMaterial, "exact-rates", "fallback");
            return new ExactStageData(Map.of(), List.of(), true);
        }
    }

    /**
     * 只在逐局明细完整且每队恰有五名选手时发布战队指标，避免用选手聚合数据猜测转会或替补归属。
     */
    private static List<TeamStageDetailMetric> aggregateTeamMetrics(
            java.util.Collection<MatchPlayerMetricSourceRecord> metrics,
            Map<GameTeamKey, MatchTeamMetricSourceRecord> extendedMetrics,
            boolean extendedMetricsAvailable) {
        Map<GameTeamKey, TeamGameMetricAccumulator> games = new HashMap<>();
        Map<GameKey, Set<Long>> teamsByGame = new HashMap<>();
        for (MatchPlayerMetricSourceRecord metric : metrics) {
            GameTeamKey key = new GameTeamKey(metric.matchId(), metric.bo(), metric.teamId());
            games.computeIfAbsent(key, ignored -> new TeamGameMetricAccumulator()).add(metric);
            teamsByGame.computeIfAbsent(new GameKey(metric.matchId(), metric.bo()), ignored -> new java.util.HashSet<>())
                    .add(metric.teamId());
        }
        if (games.isEmpty()) {
            return List.of();
        }
        if (teamsByGame.values().stream().anyMatch(teams -> teams.size() != 2)) {
            throw new TjStatsSourceException("PLAYER_EXACT: 比赛详情缺少一方战队数据，不能发布战队逐局指标");
        }

        Map<Long, TeamStageMetricAccumulator> totals = new java.util.TreeMap<>();
        for (Map.Entry<GameTeamKey, TeamGameMetricAccumulator> entry : games.entrySet()) {
            TeamGameMetricAccumulator game = entry.getValue();
            if (game.playerCount != 5) {
                throw new TjStatsSourceException("PLAYER_EXACT: 单局战队选手数不是 5，不能发布战队逐局指标");
            }
            MatchTeamMetricSourceRecord extended = extendedMetrics.get(entry.getKey());
            MatchTeamMetricSourceRecord opponent = extendedMetrics.values().stream()
                    .filter(candidate -> candidate.matchId() == entry.getKey().matchId()
                            && candidate.bo() == entry.getKey().bo()
                            && candidate.teamId() != entry.getKey().teamId())
                    .findFirst().orElse(null);
            if (extendedMetricsAvailable && extended == null) {
                extendedMetricsAvailable = false;
            }
            if (extendedMetricsAvailable && opponent == null) {
                extendedMetricsAvailable = false;
            }
            totals.computeIfAbsent(entry.getKey().teamId(), ignored -> new TeamStageMetricAccumulator())
                    .add(game, extended, opponent);
        }
        final boolean publishExtendedMetrics = extendedMetricsAvailable;
        return totals.entrySet().stream()
                .map(entry -> new TeamStageDetailMetric(
                        entry.getKey(), entry.getValue().gameCount, entry.getValue().totalAssists,
                        entry.getValue().totalDamage,
                        publishExtendedMetrics ? entry.getValue().totalGameSeconds : null,
                        publishExtendedMetrics ? entry.getValue().totalGold : null,
                        publishExtendedMetrics ? entry.getValue().totalWardsPlaced : null,
                        publishExtendedMetrics ? entry.getValue().totalWardsKilled : null,
                        publishExtendedMetrics ? entry.getValue().totalMinionKills : null,
                        publishExtendedMetrics ? entry.getValue().totalDragons : null,
                        publishExtendedMetrics ? entry.getValue().totalDragonOpportunities : null,
                        publishExtendedMetrics ? entry.getValue().totalBarons : null,
                        publishExtendedMetrics ? entry.getValue().totalBaronOpportunities : null,
                        publishExtendedMetrics ? entry.getValue().totalTurrets : null,
                        publishExtendedMetrics ? entry.getValue().totalTurretsLost : null,
                        publishExtendedMetrics ? entry.getValue().firstBloodGames : null))
                .toList();
    }

    private static void addMatchMetrics(
            Map<GamePlayerKey, MatchPlayerMetricSourceRecord> target,
            List<MatchPlayerMetricSourceRecord> metrics) {
        for (MatchPlayerMetricSourceRecord metric : metrics) {
            GamePlayerKey key = new GamePlayerKey(
                    metric.matchId(), metric.bo(), metric.playerId());
            if (target.putIfAbsent(key, metric) != null) {
                throw new TjStatsSourceException(
                        "PLAYER_EXACT: 比赛详情存在重复选手记录 " + key);
            }
        }
    }

    private static void addMatchTeamMetrics(
            Map<GameTeamKey, MatchTeamMetricSourceRecord> target,
            List<MatchTeamMetricSourceRecord> metrics) {
        for (MatchTeamMetricSourceRecord metric : metrics) {
            GameTeamKey key = new GameTeamKey(metric.matchId(), metric.bo(), metric.teamId());
            if (target.putIfAbsent(key, metric) != null) {
                throw new TjStatsSourceException("PLAYER_EXACT: 比赛详情存在重复战队记录 " + key);
            }
        }
    }

    private Map<Long, String> loadExistingMatchDetails(long seasonId, long stageId) {
        List<String> rawDetails = collectionMapper.findMatchDetailResponses(seasonId, stageId);
        if (rawDetails == null || rawDetails.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> result = new HashMap<>();
        for (String rawDetail : rawDetails) {
            if (rawDetail == null || rawDetail.isBlank()) {
                continue;
            }
            try {
                long matchId = objectMapper.readTree(rawDetail).path("data").path("matchId").asLong(0L);
                if (matchId > 0) {
                    result.put(matchId, rawDetail);
                }
            } catch (JsonProcessingException exception) {
                throw new TjStatsSourceException("PLAYER_EXACT: 本地比赛详情不是合法 JSON", exception);
            }
        }
        return result;
    }

    private PlayerHeroRecordPayload parseCompleteHeroRecords(String rawJson,
                                                             PlayerStatSourceRecord player) {
        if (rawJson == null || rawJson.isBlank() || player.playerId() == null) {
            return null;
        }
        try {
            PlayerHeroRecordPayload payload = parser.parsePlayerHeroRecords(rawJson, player.playerId());
            return matchesOfficialTotals(payload, player) ? payload : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean matchesOfficialTotals(PlayerHeroRecordPayload payload,
                                                 PlayerStatSourceRecord player) {
        if (payload.records().size() != player.boCount()) {
            return false;
        }
        long kills = payload.records().stream().mapToLong(HeroRecordSourceRecord::kill).sum();
        long assists = payload.records().stream().mapToLong(HeroRecordSourceRecord::assist).sum();
        long deaths = payload.records().stream().mapToLong(HeroRecordSourceRecord::death).sum();
        return kills == player.totalKills()
                && assists == player.totalAssists()
                && deaths == player.totalDeath();
    }

    private static ExactPlayerRates aggregateExactRates(List<MatchPlayerMetricSourceRecord> metrics) {
        long participation = 0L;
        long teamKills = 0L;
        BigDecimal playerDamage = BigDecimal.ZERO;
        BigDecimal teamDamage = BigDecimal.ZERO;
        BigDecimal playerGold = BigDecimal.ZERO;
        BigDecimal teamGold = BigDecimal.ZERO;
        for (MatchPlayerMetricSourceRecord metric : metrics) {
            participation += metric.kills() + metric.assists();
            teamKills += metric.teamKills();
            playerDamage = playerDamage.add(metric.heroDamage());
            teamDamage = teamDamage.add(metric.teamHeroDamage());
            playerGold = playerGold.add(metric.playerGold());
            teamGold = teamGold.add(metric.teamGold());
        }
        return new ExactPlayerRates(
                metrics.isEmpty() ? null : playerDamage.divide(BigDecimal.valueOf(metrics.size()), MathContext.DECIMAL128),
                teamKills > 0
                        ? BigDecimal.valueOf(participation)
                        .divide(BigDecimal.valueOf(teamKills), MathContext.DECIMAL128) : null,
                teamDamage.signum() > 0 ? playerDamage.divide(teamDamage, MathContext.DECIMAL128) : null,
                teamGold.signum() > 0 ? playerGold.divide(teamGold, MathContext.DECIMAL128) : null);
    }

    private static BigDecimal exactRate(Map<Long, ExactPlayerRates> exactRates,
                                       Long playerId,
                                       java.util.function.Function<ExactPlayerRates, BigDecimal> getter,
                                       BigDecimal fallback) {
        ExactPlayerRates rates = playerId == null ? null : exactRates.get(playerId);
        BigDecimal exact = rates == null ? null : getter.apply(rates);
        return exact == null ? fallback : exact;
    }

    private void markFailed(long runId, RuntimeException exception) {
        String message = exception.getMessage();
        String errorMessage = message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(1900, message.length()));
        try {
            collectionMapper.finishRun(
                    runId, "FAILED", OffsetDateTime.now(ZoneOffset.UTC), 0, errorMessage);
        } catch (RuntimeException finishException) {
            exception.addSuppressed(finishException);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化采集数据", exception);
        }
    }

    private void storeRawResponse(long runId,
                                  String endpoint,
                                  long seasonId,
                                  long stageId,
                                  long playerId,
                                  String rawJson,
                                  OffsetDateTime collectedAt) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("seasonId", seasonId);
        parameters.put("stageIds", stageId);
        parameters.put("playerId", playerId);
        collectionMapper.insertRawResponse(
                runId, endpoint, toJson(parameters), rawJson, sha256(rawJson), collectedAt);
    }

    private void storeMatchDetailRawResponse(long runId,
                                             long seasonId,
                                             long stageId,
                                             long matchId,
                                             String rawJson,
                                             OffsetDateTime collectedAt) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("seasonId", seasonId);
        parameters.put("stageIds", stageId);
        parameters.put("matchId", matchId);
        collectionMapper.insertRawResponse(
                runId, "/compound/matchDetail", toJson(parameters), rawJson,
                sha256(rawJson), collectedAt);
    }

    private static long requirePlayerId(PlayerStatSourceRecord player, long stageId) {
        if (player.playerId() == null || player.playerId() <= 0) {
            throw new TjStatsSourceException(
                    "PLAYER_EXACT: 赛段 " + stageId + " 的选手缺少有效 playerId: " + player.playerName());
        }
        return player.playerId();
    }

    private static void appendHashMaterial(StringBuilder target, String label, String rawJson) {
        target.append(label.length()).append(':').append(label)
                .append(':').append(rawJson.length()).append(':').append(rawJson);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private record PlayerStageCandidate(
            long stageId,
            String contentHash,
            OffsetDateTime collectedAt,
            List<PlayerStatSourceRecord> players,
            ExactStageData exactData
    ) {
    }

    private record ExactStageData(
            Map<Long, ExactPlayerRates> playerRates,
            List<TeamStageDetailMetric> teamMetrics,
            boolean fallback
    ) {
    }

    private record TeamStageDetailMetric(long teamId, long gameCount, long totalAssists, BigDecimal totalDamage,
                                         Long totalGameSeconds, BigDecimal totalGold, Long totalWardsPlaced,
                                         Long totalWardsKilled, Long totalMinionKills, Long totalDragons,
                                         Long totalDragonOpportunities, Long totalBarons, Long totalBaronOpportunities,
                                         Long totalTurrets, Long totalTurretsLost, Long firstBloodGames) {
    }

    private record ExactPlayerRates(
            BigDecimal damagePerGame,
            BigDecimal killParticipantPercent,
            BigDecimal damagePercent,
            BigDecimal goldPercent
    ) {
    }

    private record GamePlayerKey(long matchId, long bo, long playerId) {
    }

    private record GameKey(long matchId, long bo) {
    }

    private record GameTeamKey(long matchId, long bo, long teamId) {
    }

    private static final class TeamGameMetricAccumulator {
        private int playerCount;
        private long totalAssists;
        private BigDecimal totalDamage = BigDecimal.ZERO;

        void add(MatchPlayerMetricSourceRecord metric) {
            playerCount++;
            totalAssists += metric.assists();
            totalDamage = totalDamage.add(metric.heroDamage());
        }
    }

    private static final class TeamStageMetricAccumulator {
        private long gameCount;
        private long totalAssists;
        private BigDecimal totalDamage = BigDecimal.ZERO;
        private long totalGameSeconds;
        private BigDecimal totalGold = BigDecimal.ZERO;
        private long totalWardsPlaced;
        private long totalWardsKilled;
        private long totalMinionKills;
        private long totalDragons;
        private long totalDragonOpportunities;
        private long totalBarons;
        private long totalBaronOpportunities;
        private long totalTurrets;
        private long totalTurretsLost;
        private long firstBloodGames;

        void add(TeamGameMetricAccumulator game, MatchTeamMetricSourceRecord extended,
                 MatchTeamMetricSourceRecord opponent) {
            gameCount++;
            totalAssists += game.totalAssists;
            totalDamage = totalDamage.add(game.totalDamage);
            if (extended != null) {
                totalGameSeconds += extended.gameTimeSeconds();
                totalGold = totalGold.add(extended.gold());
                totalWardsPlaced += extended.wardPlaced();
                totalWardsKilled += extended.wardKilled();
                totalMinionKills += extended.minionKills();
                totalDragons += extended.dragonAmount();
                totalBarons += extended.baronAmount();
                totalTurrets += extended.turretAmount();
                firstBloodGames += extended.firstBlood() ? 1 : 0;
                if (opponent != null) {
                    totalDragonOpportunities += extended.dragonAmount() + opponent.dragonAmount();
                    totalBaronOpportunities += extended.baronAmount() + opponent.baronAmount();
                    totalTurretsLost += opponent.turretAmount();
                }
            }
        }
    }
}
