package com.loldatahub.collector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.infrastructure.mapper.ChampionStatWriteMapper;
import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionPositionPlayerStageStatWrite;
import com.loldatahub.infrastructure.model.ChampionStageStatWrite;
import com.loldatahub.infrastructure.model.ChampionWrite;
import com.loldatahub.source.TjStatsClient;
import com.loldatahub.source.TjStatsResponseParser;
import com.loldatahub.source.TjStatsSourceException;
import com.loldatahub.source.model.HeroStagePayload;
import com.loldatahub.source.model.PlayerHeroRecordPayload;
import com.loldatahub.source.model.PlayerStatSourceRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

@Service
public class HeroCollectionService {
    private final TjStatsClient client;
    private final TjStatsResponseParser parser;
    private final ObjectMapper objectMapper;
    private final CollectionMapper collectionMapper;
    private final ChampionStatWriteMapper writeMapper;
    private final SystemStateMapper systemStateMapper;
    private final CatalogCollectionService catalogCollectionService;
    private final TransactionTemplate transactionTemplate;

    public HeroCollectionService(TjStatsClient client,
                                 TjStatsResponseParser parser,
                                 ObjectMapper objectMapper,
                                 CollectionMapper collectionMapper,
                                 ChampionStatWriteMapper writeMapper,
                                 SystemStateMapper systemStateMapper,
                                 CatalogCollectionService catalogCollectionService,
                                 TransactionTemplate transactionTemplate) {
        this.client = client;
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.collectionMapper = collectionMapper;
        this.writeMapper = writeMapper;
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
        collectionMapper.insertRun("HERO", seasonId, toJson(normalizedStageIds), startedAt, holder);
        long runId = holder.getId();

        try {
            catalogCollectionService.sync(seasonId);
            List<Long> unchanged = new ArrayList<>();
            List<HeroStageCandidate> changedStages = new ArrayList<>();

            for (Long stageId : normalizedStageIds) {
                OffsetDateTime collectedAt = OffsetDateTime.now(ZoneOffset.UTC);
                String rawHeroJson = client.fetchHeroStatistics(seasonId, stageId);
                storeRawResponse(runId, "/compound/public/hero", seasonId, stageId, null,
                        rawHeroJson, collectedAt);
                var payload = parser.parseHeroStage(rawHeroJson);

                String rawPlayerJson = client.fetchPlayerStatistics(seasonId, stageId);
                storeRawResponse(runId, "/compound/public/player", seasonId, stageId, null,
                        rawPlayerJson, collectedAt);
                List<PlayerStatSourceRecord> players = parser.parsePlayerStage(rawPlayerJson).stream()
                        .sorted(Comparator.comparingLong(player -> requirePlayerId(player, stageId)))
                        .toList();

                List<PlayerHeroRecordPayload> playerHeroRecords = new ArrayList<>();
                StringBuilder hashMaterial = new StringBuilder();
                appendHashMaterial(hashMaterial, "hero", rawHeroJson);
                appendHashMaterial(hashMaterial, "player", rawPlayerJson);
                for (PlayerStatSourceRecord player : players) {
                    long playerId = requirePlayerId(player, stageId);
                    String rawRecordJson = client.fetchPlayerHeroRecords(playerId, seasonId, stageId);
                    storeRawResponse(runId, "/compound/heroRecord", seasonId, stageId, playerId,
                            rawRecordJson, collectedAt);
                    playerHeroRecords.add(parser.parsePlayerHeroRecords(rawRecordJson, playerId));
                    appendHashMaterial(hashMaterial, "heroRecord:" + playerId, rawRecordJson);
                }
                HeroPositionStatAssembler.Result positionData = HeroPositionStatAssembler.assemble(
                        payload, players, playerHeroRecords
                );
                String contentHash = sha256(hashMaterial.toString());

                if (contentHash.equals(collectionMapper.findCurrentContentHash(seasonId, stageId))) {
                    unchanged.add(stageId);
                    continue;
                }

                changedStages.add(new HeroStageCandidate(
                        stageId, contentHash, collectedAt, payload, positionData
                ));
            }

            if (changedStages.isEmpty()) {
                collectionMapper.finishRun(runId, "NO_CHANGE", OffsetDateTime.now(ZoneOffset.UTC), 0, null);
                return new CollectionResult(runId, "NO_CHANGE", 0, unchanged);
            }

            int changedRecords = changedStages.stream()
                    .mapToInt(candidate -> 1 + candidate.payload().heroes().size()
                            + candidate.positionData().rows().size())
                    .sum();
            Integer committedRecords = transactionTemplate.execute(status -> {
                for (HeroStageCandidate candidate : changedStages) {
                    long stageId = candidate.stageId();
                    HeroStagePayload payload = candidate.payload();
                    writeMapper.upsertStageCurrent(
                            seasonId, stageId, payload.sampleBaseCount(), payload.gameVersion().toString(),
                            SourceTimeParser.fromEpochSeconds(payload.updatedAt()), candidate.contentHash(),
                            candidate.collectedAt(), runId
                    );
                    writeMapper.deletePositionCurrentForStage(seasonId, stageId);
                    writeMapper.deleteCurrentForStage(seasonId, stageId);
                    for (var hero : payload.heroes()) {
                        String chineseName = hero.heroCnName() == null || hero.heroCnName().isBlank()
                                ? hero.heroName()
                                : hero.heroCnName();
                        writeMapper.upsertChampion(new ChampionWrite(
                                hero.heroId(), hero.heroName(), chineseName, hero.heroCnTitle(),
                                normalizeLogoUrl(hero.heroLogo()),
                                toJson(candidate.positionData().positionsByChampion()
                                        .getOrDefault(hero.heroId(), List.of()))
                        ));
                        ChampionStageStatWrite stat = new ChampionStageStatWrite(
                                runId, seasonId, stageId, hero.heroId(), hero.pickCount(), hero.banCount(),
                                hero.bpCount(), hero.winningCount(), hero.totalKills(), hero.totalDeath(),
                                hero.totalAssists(), hero.pickRate(), hero.banRate(), hero.bPRate(),
                                hero.winningRate(), hero.mostUsePlayerId(), hero.mostUsePlayerName(),
                                toJson(candidate.positionData().positionsByChampion()
                                        .getOrDefault(hero.heroId(), List.of())),
                                candidate.collectedAt()
                        );
                        writeMapper.upsertCurrent(stat);
                        writeMapper.insertSnapshot(stat);
                    }
                    for (HeroPositionStatAssembler.PositionPlayerAggregate row
                            : candidate.positionData().rows()) {
                        ChampionPositionPlayerStageStatWrite stat =
                                new ChampionPositionPlayerStageStatWrite(
                                        runId, seasonId, stageId, row.championId(), row.position(),
                                        row.playerId(), row.playerName(), row.pickCount(), row.winningCount(),
                                        row.totalKills(), row.totalDeaths(), row.totalAssists(), candidate.collectedAt()
                                );
                        writeMapper.upsertPositionCurrent(stat);
                        writeMapper.insertPositionSnapshot(stat);
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

    private void storeRawResponse(long runId,
                                  String endpoint,
                                  long seasonId,
                                  long stageId,
                                  Long playerId,
                                  String rawJson,
                                  OffsetDateTime collectedAt) {
        java.util.Map<String, Object> parameters = new java.util.LinkedHashMap<>();
        parameters.put("seasonId", seasonId);
        parameters.put("stageIds", stageId);
        if (playerId != null) {
            parameters.put("playerId", playerId);
        }
        collectionMapper.insertRawResponse(
                runId, endpoint, toJson(parameters), rawJson, sha256(rawJson), collectedAt
        );
    }

    private static long requirePlayerId(PlayerStatSourceRecord player, long stageId) {
        if (player.playerId() == null || player.playerId() <= 0) {
            throw new TjStatsSourceException(
                    "HERO_POSITION: 赛段 " + stageId + " 的选手缺少有效 playerId：" + player.playerName());
        }
        return player.playerId();
    }

    private static void appendHashMaterial(StringBuilder target, String label, String rawJson) {
        target.append(label.length()).append(':').append(label)
                .append(':').append(rawJson.length()).append(':').append(rawJson);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static String normalizeLogoUrl(String logoUrl) {
        if (logoUrl != null && logoUrl.startsWith("http://game.gtimg.cn/")) {
            return "https://" + logoUrl.substring("http://".length());
        }
        return logoUrl;
    }

    private record HeroStageCandidate(
            long stageId,
            String contentHash,
            OffsetDateTime collectedAt,
            HeroStagePayload payload,
            HeroPositionStatAssembler.Result positionData
    ) {
    }
}
