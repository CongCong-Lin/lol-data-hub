package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamStatWriteMapper;
import com.loldatahub.infrastructure.mapper.TeamStatisticsMapper;
import com.loldatahub.infrastructure.model.TeamStageStatWrite;
import com.loldatahub.infrastructure.model.TeamWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.model.TeamStatSourceRecord;
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
public class TeamCollectionService {
    private final TjStatsClient client;
    private final TjStatsResponseParser parser;
    private final ObjectMapper objectMapper;
    private final CollectionMapper collectionMapper;
    private final TeamStatWriteMapper writeMapper;
    private final TeamStatisticsMapper statisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final CatalogCollectionService catalogCollectionService;
    private final TransactionTemplate transactionTemplate;

    public TeamCollectionService(TjStatsClient client,
                                 TjStatsResponseParser parser,
                                 ObjectMapper objectMapper,
                                 CollectionMapper collectionMapper,
                                 TeamStatWriteMapper writeMapper,
                                 TeamStatisticsMapper statisticsMapper,
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
        collectionMapper.insertRun("TEAM", seasonId, toJson(normalizedStageIds), startedAt, holder);
        long runId = holder.getId();

        try {
            catalogCollectionService.sync(seasonId);
            List<Long> unchanged = new ArrayList<>();
            List<TeamStageCandidate> changedStages = new ArrayList<>();

            for (Long stageId : normalizedStageIds) {
                String rawJson = client.fetchTeamStatistics(seasonId, stageId);
                String contentHash = sha256(rawJson);
                OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);

                collectionMapper.insertRawResponse(
                        runId,
                        "/compound/public/team",
                        toJson(java.util.Map.of("seasonId", seasonId, "stageIds", stageId)),
                        rawJson,
                        contentHash,
                        collectedAt
                );

                var teams = parser.parseTeamStage(rawJson);

                if (contentHash.equals(statisticsMapper.findCurrentContentHash(seasonId, stageId))) {
                    unchanged.add(stageId);
                    continue;
                }

                changedStages.add(new TeamStageCandidate(stageId, contentHash, collectedAt, teams));
            }

            if (changedStages.isEmpty()) {
                collectionMapper.finishRun(runId, "NO_CHANGE", OffsetDateTime.now(ZoneOffset.UTC), 0, null);
                return new CollectionResult(runId, "NO_CHANGE", 0, unchanged);
            }

            int changedRecords = changedStages.stream()
                    .mapToInt(candidate -> 1 + candidate.teams().size())
                    .sum();
            Integer committedRecords = transactionTemplate.execute(status -> {
                for (TeamStageCandidate candidate : changedStages) {
                    long stageId = candidate.stageId();
                    writeMapper.upsertCollectionCurrent(
                            seasonId, stageId, candidate.contentHash(), candidate.collectedAt(), runId
                    );
                    writeMapper.deleteCurrentForStage(seasonId, stageId);
                    for (var team : candidate.teams()) {
                        writeMapper.upsertTeam(new TeamWrite(
                                team.teamId(), team.teamName(), team.teamLogo()
                        ));
                        TeamStageStatWrite stat = new TeamStageStatWrite(
                                runId, seasonId, stageId, team.teamId(),
                                team.matchCount(), team.gameCount(), team.matchWinCount(),
                                team.totalKills(), team.totalDeath(),
                                team.wardPlacedPerGameTeam(), team.wardKilledPerGameTeam(),
                                team.goldPerGameTeam(), team.baronKillPerGameTeam(),
                                team.drakeKillPerGameTeam(), candidate.collectedAt()
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

    private record TeamStageCandidate(
            long stageId,
            String contentHash,
            OffsetDateTime collectedAt,
            List<TeamStatSourceRecord> teams
    ) {
    }
}
