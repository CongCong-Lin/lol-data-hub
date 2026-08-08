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

    public CollectionResult collect(long seasonId, List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            throw new IllegalArgumentException("至少需要指定一个赛段");
        }
        List<Long> normalizedStageIds = stageIds.stream().distinct().sorted().toList();
        catalogCollectionService.sync(seasonId);

        CollectionMapper.GeneratedId holder = new CollectionMapper.GeneratedId();
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        collectionMapper.insertRun("PLAYER", seasonId, toJson(normalizedStageIds), startedAt, holder);
        long runId = holder.getId();
        int changedRecords = 0;
        List<Long> unchanged = new ArrayList<>();

        try {
            for (Long stageId : normalizedStageIds) {
                String rawJson = client.fetchPlayerStatistics(seasonId, stageId);
                String contentHash = sha256(rawJson);
                OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);

                collectionMapper.insertRawResponse(
                        runId,
                        "/compound/public/player",
                        toJson(java.util.Map.of("seasonId", seasonId, "stageIds", stageId)),
                        rawJson,
                        contentHash,
                        collectedAt
                );

                var players = parser.parsePlayerStage(rawJson);

                if (contentHash.equals(statisticsMapper.findCurrentContentHash(seasonId, stageId))) {
                    unchanged.add(stageId);
                    continue;
                }

                Integer stageChanges = transactionTemplate.execute(status -> {
                    writeMapper.upsertCollectionCurrent(seasonId, stageId, contentHash, collectedAt, runId);
                    writeMapper.deleteCurrentForStage(seasonId, stageId);
                    int count = 1;
                    for (var player : players) {
                        String playerKey = PlayerIdentity.resolve(player.playerId(), player.playerName());
                        writeMapper.upsertPlayer(new PlayerWrite(
                                playerKey, player.playerId(), player.playerName(), player.playerAvatar()
                        ));
                        PlayerStageStatWrite stat = new PlayerStageStatWrite(
                                runId, seasonId, stageId, playerKey,
                                player.teamName(), player.teamLogo(), player.playerLocation(),
                                player.matchCount(), player.mvpCount(), player.mvpVotes(),
                                player.totalKills(), player.totalAssists(), player.totalDeath(),
                                player.goldPerGame(), player.creepScorePerGame(),
                                player.wardPlacedPerGame(), player.wardKilledPerGame(),
                                player.killParticipantPercent(), player.goldGapPerGame(),
                                player.damagePercent(), player.goldPercent(),
                                collectedAt
                        );
                        writeMapper.upsertCurrent(stat);
                        writeMapper.insertSnapshot(stat);
                        count++;
                    }
                    return count;
                });
                changedRecords += stageChanges == null ? 0 : stageChanges;
            }

            if (changedRecords > 0) {
                systemStateMapper.incrementDataVersion();
            }
            String status = changedRecords > 0 ? "SUCCESS" : "NO_CHANGE";
            collectionMapper.finishRun(runId, status, OffsetDateTime.now(ZoneOffset.UTC), changedRecords, null);
            return new CollectionResult(runId, status, changedRecords, unchanged);
        } catch (RuntimeException exception) {
            if (changedRecords > 0) {
                systemStateMapper.incrementDataVersion();
            }
            String message = exception.getMessage();
            collectionMapper.finishRun(
                    runId, "FAILED", OffsetDateTime.now(ZoneOffset.UTC), changedRecords,
                    message == null ? exception.getClass().getSimpleName() : message.substring(0, Math.min(1900, message.length()))
            );
            throw exception;
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
}
