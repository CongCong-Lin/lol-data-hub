package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerIdentity;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatWriteMapper;
import com.loldatahub.infrastructure.mapper.PlayerStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerStageStatWrite;
import com.loldatahub.infrastructure.model.PlayerWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class PlayerCollectionService {
    static final String CONTENT_SCHEMA_VERSION = "player-v2-active-by-series-or-game-count";

    private final TjStatsClient client;
    private final TjStatsResponseParser parser;
    private final ObjectMapper objectMapper;
    private final CollectionMapper collectionMapper;
    private final PlayerStatWriteMapper writeMapper;
    private final PlayerStatisticsMapper statisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final CatalogCollectionService catalogCollectionService;
    private final TransactionTemplate transactionTemplate;

    public PlayerCollectionService(TjStatsClient client,
                                   TjStatsResponseParser parser,
                                   ObjectMapper objectMapper,
                                   CollectionMapper collectionMapper,
                                   PlayerStatWriteMapper writeMapper,
                                   PlayerStatisticsMapper statisticsMapper,
                                   SystemStateMapper systemStateMapper,
                                   CatalogCollectionService catalogCollectionService,
                                   TransactionTemplate transactionTemplate) {
        this.client = client;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.collectionMapper = collectionMapper;
        this.writeMapper = writeMapper;
        this.statisticsMapper = statisticsMapper;
        this.systemStateMapper = systemStateMapper;
        this.catalogCollectionService = catalogCollectionService;
        this.transactionTemplate = transactionTemplate;
    }

    // 单 JVM 内串行保护，避免同表并发 delete/upsert 竞争；分布式部署仍需外部锁
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
                String contentHash = sha256(CONTENT_SCHEMA_VERSION + "\n" + rawJson);
                OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);

                collectionMapper.insertRawResponse(
                        runId,
                        "/compound/public/player",
                        toJson(java.util.Map.of("seasonId", seasonId, "stageIds", stageId)),
                        rawJson,
                        rawHash,
                        collectedAt
                );

                var players = parser.parsePlayerStage(rawJson);

                if (contentHash.equals(statisticsMapper.findCurrentContentHash(seasonId, stageId))) {
                    unchanged.add(stageId);
                    continue;
                }

                changedStages.add(new PlayerStageCandidate(stageId, contentHash, collectedAt, players));
            }

            if (changedStages.isEmpty()) {
                collectionMapper.finishRun(runId, "NO_CHANGE", OffsetDateTime.now(ZoneOffset.UTC), 0, null);
                return new CollectionResult(runId, "NO_CHANGE", 0, unchanged);
            }

            int changedRecords = changedStages.stream()
                    .mapToInt(candidate -> 1 + candidate.players().size())
                    .sum();
            Integer committedRecords = transactionTemplate.execute(status -> {
                for (PlayerStageCandidate candidate : changedStages) {
                    long stageId = candidate.stageId();
                    writeMapper.upsertCollectionCurrent(
                            seasonId, stageId, candidate.contentHash(), candidate.collectedAt(), runId
                    );
                    writeMapper.deleteCurrentForStage(seasonId, stageId);
                    for (var player : candidate.players()) {
                        String playerKey = PlayerIdentity.resolve(player.playerId(), player.playerName());
                        writeMapper.upsertPlayer(new PlayerWrite(
                                playerKey, player.playerId(), player.playerName(), player.playerAvatar()
                        ));
                        PlayerStageStatWrite stat = new PlayerStageStatWrite(
                                runId, seasonId, stageId, playerKey,
                                player.teamName(), player.teamLogo(), player.playerLocation(),
                                player.matchCount(), player.boCount(), player.mvpCount(), player.mvpVotes(),
                                player.totalKills(), player.totalAssists(), player.totalDeath(),
                                player.goldPerGame(), player.creepScorePerGame(),
                                player.wardPlacedPerGame(), player.wardKilledPerGame(),
                                player.killParticipantPercent(), player.goldGapPerGame(),
                                player.damagePercent(), player.goldPercent(),
                                candidate.collectedAt()
                        );
                        writeMapper.upsertCurrent(stat);
                        writeMapper.insertSnapshot(stat);
                    }
                }
                systemStateMapper.incrementDataVersion();
                collectionMapper.finishRun(
                        runId, "SUCCESS", OffsetDateTime.now(ZoneOffset.UTC), changedRecords, null
                );
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

    private void markFailed(long runId, RuntimeException exception) {
        String message = exception.getMessage();
        String errorMessage = message == null
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(1900, message.length()));
        try {
            collectionMapper.finishRun(
                    runId, "FAILED", OffsetDateTime.now(ZoneOffset.UTC), 0, errorMessage
            );
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

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private record PlayerStageCandidate(
            long stageId,
            String contentHash,
            OffsetDateTime collectedAt,
            List<PlayerStatSourceRecord> players
    ) {
    }
}
