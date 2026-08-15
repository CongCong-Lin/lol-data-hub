package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.MatchGameWriteMapper;
import com.loldatahub.infrastructure.model.MatchDetailSourceRow;
import com.loldatahub.infrastructure.model.MatchGamePlayerWrite;
import com.loldatahub.infrastructure.model.MatchGameWrite;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import com.loldatahub.source.model.MatchPlayerGameSourceRecord;
import com.loldatahub.source.model.MatchPlayerMetricSourceRecord;
import com.loldatahub.source.model.MatchTeamMetricSourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 对局明细回填服务：从 source_raw_response 中已保存的官网 matchDetail 原始响应
 * 解析逐局对局与选手表现，重建 match_game_current / match_game_player_current。
 *
 * <p>回填不重新请求官网，只消费既有原始响应，因此可重复执行且不改变原有采集幂等语义；
 * 单场比赛解析失败时跳过该场并计数，保证历史脏数据不会阻断整个赛段回填。</p>
 */
@Service
public class MatchGameBackfillService {
    private static final Logger log = LoggerFactory.getLogger(MatchGameBackfillService.class);
    private static final DateTimeFormatter SIMPLE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TjStatsResponseParser parser;
    private final ObjectMapper objectMapper;
    private final CollectionMapper collectionMapper;
    private final MatchGameWriteMapper writeMapper;
    private final TransactionTemplate transactionTemplate;

    public MatchGameBackfillService(TjStatsResponseParser parser,
                                    ObjectMapper objectMapper,
                                    CollectionMapper collectionMapper,
                                    MatchGameWriteMapper writeMapper,
                                    TransactionTemplate transactionTemplate) {
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.collectionMapper = collectionMapper;
        this.writeMapper = writeMapper;
        this.transactionTemplate = transactionTemplate;
    }

    /** 单场比赛的解析结果：按局号分组的两队指标与十人指标。 */
    private record ParsedMatch(long matchId, LocalDateTime startTime,
                               Map<Long, List<MatchTeamMetricSourceRecord>> teamsByGame,
                               Map<Long, List<MatchPlayerMetricSourceRecord>> playersByGame,
                               Map<Long, List<MatchPlayerGameSourceRecord>> gamesByGame) {
    }

    // 单 JVM 内串行保护，避免同表并发 delete/upsert 竞争；分布式部署仍需外部锁
    public synchronized CollectionResult collect(long seasonId, List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个赛段");
        }
        List<Long> normalizedStageIds = stageIds.stream().distinct().sorted().toList();

        CollectionMapper.GeneratedId holder = new CollectionMapper.GeneratedId();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        collectionMapper.insertRun("MATCH_GAME", seasonId, toJson(normalizedStageIds), startedAt, holder);
        long runId = holder.getId();

        List<StageCandidate> candidates = new ArrayList<>();
        int failedMatches = 0;
        try {
            for (Long stageId : normalizedStageIds) {
                StageCandidate candidate = new StageCandidate(seasonId, stageId, runId, startedAt);
                for (MatchDetailSourceRow row : collectionMapper.findLatestMatchDetails(seasonId, stageId)) {
                    try {
                        candidate.add(parseMatch(row.body(), row.matchId()));
                    } catch (TjStatsSourceException exception) {
                        failedMatches++;
                        log.warn("对局明细回填：跳过解析失败的比赛，seasonId={}, stageId={}, matchId={}：{}",
                                seasonId, stageId, row.matchId(), exception.getMessage());
                    }
                }
                candidates.add(candidate);
            }

            int changedRecords = candidates.stream().mapToInt(StageCandidate::totalRows).sum();
            if (changedRecords == 0) {
                collectionMapper.finishRun(runId, "NO_CHANGE", OffsetDateTime.now(ZoneOffset.UTC), 0,
                        failedMatches == 0 ? null : failedMatches + " 场比赛解析失败，无可用对局数据");
                return new CollectionResult(runId, "NO_CHANGE", 0, normalizedStageIds);
            }

            transactionTemplate.execute(status -> {
                for (StageCandidate candidate : candidates) {
                    writeMapper.deletePlayerCurrentForStage(seasonId, candidate.stageId());
                    writeMapper.deleteCurrentForStage(seasonId, candidate.stageId());
                    for (MatchGameWrite game : candidate.games()) {
                        writeMapper.upsertMatchGame(game);
                        writeMapper.insertMatchGameSnapshot(game);
                    }
                    for (MatchGamePlayerWrite player : candidate.players()) {
                        writeMapper.upsertMatchGamePlayer(player);
                        writeMapper.insertMatchGamePlayerSnapshot(player);
                    }
                }
                // 回填基于已持久化的原始响应，不改变聚合表的数据版本
                return null;
            });
            collectionMapper.finishRun(runId, "SUCCESS", OffsetDateTime.now(ZoneOffset.UTC), changedRecords,
                    failedMatches == 0 ? null : failedMatches + " 场比赛解析失败已跳过");
            return new CollectionResult(runId, "SUCCESS", changedRecords, List.of());
        } catch (RuntimeException exception) {
            String message = String.valueOf(exception.getMessage());
            collectionMapper.finishRun(runId, "FAILED", OffsetDateTime.now(ZoneOffset.UTC), 0,
                    message.substring(0, Math.min(500, message.length())));
            throw exception;
        }
    }

    private ParsedMatch parseMatch(String rawJson, long matchId) {
        List<MatchPlayerGameSourceRecord> games = parser.parseMatchPlayerGames(rawJson, matchId);
        List<MatchPlayerMetricSourceRecord> metrics = parser.parseMatchPlayerMetrics(rawJson, matchId);
        List<MatchTeamMetricSourceRecord> teams = parser.parseMatchTeamMetrics(rawJson, matchId);
        LocalDateTime startTime = extractStartTime(rawJson);
        Map<Long, List<MatchTeamMetricSourceRecord>> teamsByGame = new TreeMap<>();
        Map<Long, List<MatchPlayerMetricSourceRecord>> playersByGame = new TreeMap<>();
        Map<Long, List<MatchPlayerGameSourceRecord>> gamesByGame = new TreeMap<>();
        for (MatchTeamMetricSourceRecord team : teams) {
            teamsByGame.computeIfAbsent(team.bo(), ignored -> new ArrayList<>()).add(team);
        }
        for (MatchPlayerMetricSourceRecord player : metrics) {
            playersByGame.computeIfAbsent(player.bo(), ignored -> new ArrayList<>()).add(player);
        }
        for (MatchPlayerGameSourceRecord player : games) {
            gamesByGame.computeIfAbsent(player.bo(), ignored -> new ArrayList<>()).add(player);
        }
        return new ParsedMatch(matchId, startTime, teamsByGame, playersByGame, gamesByGame);
    }

    /** 防御式提取比赛开始时间：matchDetail 不同版本字段名可能不同，提取失败不影响对局落库。 */
    private LocalDateTime extractStartTime(String rawJson) {
        try {
            JsonNode data = objectMapper.readTree(rawJson).path("data");
            if (data.isObject()) {
                for (String field : new String[]{"matchStartTime", "startTime", "beginTime"}) {
                    LocalDateTime parsed = tryParseTime(data.get(field));
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        } catch (IOException ignored) {
            // 原始响应无法解析为 JSON 时由后续严格解析兜底
        }
        return null;
    }

    private LocalDateTime tryParseTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            long value = node.longValue();
            if (value <= 0) {
                return null;
            }
            try {
                if (value < 10_000_000_000L) {
                    return LocalDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneOffset.UTC);
                }
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneOffset.UTC);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(text).toLocalDateTime();
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
            try {
                return LocalDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                // 继续尝试其他格式
            }
            try {
                return LocalDateTime.parse(text, SIMPLE_DATE_TIME);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("无法序列化请求参数", exception);
        }
    }

    /** 单个赛段回填候选：先内存解析，事务内统一重建。 */
    private static final class StageCandidate {
        private final long seasonId;
        private final long stageId;
        private final long runId;
        private final OffsetDateTime collectedAt;
        private final List<MatchGameWrite> games = new ArrayList<>();
        private final List<MatchGamePlayerWrite> players = new ArrayList<>();

        private StageCandidate(long seasonId, long stageId, long runId, OffsetDateTime collectedAt) {
            this.seasonId = seasonId;
            this.stageId = stageId;
            this.runId = runId;
            this.collectedAt = collectedAt;
        }

        private void add(ParsedMatch parsed) {
            for (Map.Entry<Long, List<MatchPlayerGameSourceRecord>> entry : parsed.gamesByGame().entrySet()) {
                long bo = entry.getKey();
                List<MatchTeamMetricSourceRecord> teams = parsed.teamsByGame().getOrDefault(bo, List.of());
                List<MatchPlayerMetricSourceRecord> playerMetrics =
                        parsed.playersByGame().getOrDefault(bo, List.of());
                if (teams.size() != 2) {
                    throw new TjStatsSourceException("MATCH_DETAIL: 单局战队指标数量异常，matchId="
                            + parsed.matchId() + "，bo=" + bo);
                }
                MatchTeamMetricSourceRecord first = teams.get(0);
                MatchTeamMetricSourceRecord second = teams.get(1);
                MatchTeamMetricSourceRecord teamA = first.teamId() <= second.teamId() ? first : second;
                MatchTeamMetricSourceRecord teamB = first.teamId() <= second.teamId() ? second : first;
                long winTeamId = entry.getValue().get(0).winTeamId();
                long duration = teamA.gameTimeSeconds();
                Map<Long, Long> teamKillsByTeam = new HashMap<>();
                for (MatchPlayerMetricSourceRecord metric : playerMetrics) {
                    teamKillsByTeam.putIfAbsent(metric.teamId(), metric.teamKills());
                }

                games.add(new MatchGameWrite(
                        runId, seasonId, stageId, parsed.matchId(), (int) bo, parsed.startTime(),
                        teamA.teamId(), teamB.teamId(), winTeamId, duration,
                        teamKillsByTeam.getOrDefault(teamA.teamId(), 0L),
                        teamA.totalAssists(), teamA.heroDamage(), teamA.gold(),
                        teamA.wardPlaced(), teamA.wardKilled(), teamA.minionKills(),
                        teamA.dragonAmount(), teamA.baronAmount(), teamA.turretAmount(), teamA.firstBlood(),
                        teamKillsByTeam.getOrDefault(teamB.teamId(), 0L),
                        teamB.totalAssists(), teamB.heroDamage(), teamB.gold(),
                        teamB.wardPlaced(), teamB.wardKilled(), teamB.minionKills(),
                        teamB.dragonAmount(), teamB.baronAmount(), teamB.turretAmount(), teamB.firstBlood(),
                        collectedAt));

                Map<Long, MatchPlayerMetricSourceRecord> metricsByPlayer = new HashMap<>();
                for (MatchPlayerMetricSourceRecord metric : playerMetrics) {
                    metricsByPlayer.put(metric.playerId(), metric);
                }
                for (MatchPlayerGameSourceRecord player : entry.getValue()) {
                    MatchPlayerMetricSourceRecord metric = metricsByPlayer.get(player.playerId());
                    players.add(new MatchGamePlayerWrite(
                            runId, seasonId, stageId, parsed.matchId(), (int) bo, parsed.startTime(),
                            player.playerId(), player.teamId(), player.heroId(), player.position(),
                            player.teamId() == winTeamId,
                            player.kill(), player.death(), player.assist(),
                            metric == null ? BigDecimal.ZERO : metric.heroDamage(),
                            metric == null ? BigDecimal.ZERO : metric.playerGold(),
                            metric == null ? 0L : metric.teamKills(),
                            metric == null ? BigDecimal.ZERO : metric.teamHeroDamage(),
                            metric == null ? BigDecimal.ZERO : metric.teamGold(),
                            metric == null ? null : metric.killParticipantPercent(),
                            metric == null ? null : metric.damagePercent(),
                            metric == null ? null : metric.goldPercent(),
                            collectedAt));
                }
            }
        }

        private int totalRows() {
            return games.size() + players.size();
        }

        private long stageId() {
            return stageId;
        }

        private List<MatchGameWrite> games() {
            return games;
        }

        private List<MatchGamePlayerWrite> players() {
            return players;
        }
    }
}
