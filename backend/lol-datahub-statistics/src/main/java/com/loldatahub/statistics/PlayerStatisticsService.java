package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerStatistics;
import com.loldatahub.domain.statistics.PlayerStatisticsMath;
import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.PlayerStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerAggregateRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PlayerStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(PlayerStatisticsService.class);
    private static final TypeReference<List<PlayerStatistics>> CACHE_TYPE = new TypeReference<>() { };

    private final PlayerStatisticsMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public PlayerStatisticsService(PlayerStatisticsMapper mapper,
                                   SystemStateMapper systemStateMapper,
                                   StringRedisTemplate redisTemplate,
                                   ObjectMapper objectMapper,
                                   @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.mapper = mapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public PlayerStatisticsResult query(PlayerStatisticsQuery query) {
        List<StageKey> stages = query.stages();
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        List<StageKey> collectedKeys = mapper.findCollectedStageKeys(stages);
        Set<StageKey> collectedSet = new java.util.HashSet<>(collectedKeys);
        List<StageKey> missingKeys = stages.stream()
                .filter(sk -> !collectedSet.contains(sk))
                .toList();
        if (!missingKeys.isEmpty()) {
            String missingStr = missingKeys.stream()
                    .map(StageKey::canonical)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("以下赛段尚未采集选手数据：" + missingStr);
        }

        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s3:v" + dataVersion + ":player:" + query.cacheFingerprint();
        List<PlayerStatistics> cached = readCache(cacheKey);
        if (cached != null) {
            return new PlayerStatisticsResult(dataVersion, query.minimumMatchCount(), cached.size(), cached);
        }

        List<PlayerStatistics> items = new ArrayList<>(mapper.aggregatePlayers(
                stages, query.minimumMatchCount()
        ).stream().map(row -> map(row, query)).toList());

        if (query.position() != null) {
            items = items.stream()
                    .filter(p -> p.positions().stream()
                            .anyMatch(pos -> pos.equalsIgnoreCase(query.position())))
                    .toList();
        }

        List<PlayerStatistics> sorted = new ArrayList<>(items);
        sorted.sort(comparator(query));
        writeCache(cacheKey, sorted);
        return new PlayerStatisticsResult(dataVersion, query.minimumMatchCount(), sorted.size(), sorted);
    }

    private PlayerStatistics map(PlayerAggregateRow row, PlayerStatisticsQuery query) {
        List<String> teamNames = PlayerStatisticsMath.splitCsv(row.teamNamesCsv());
        List<String> positions = PlayerStatisticsMath.splitCsv(row.positionsCsv());
        long matchCount = row.matchCount();
        return new PlayerStatistics(
                row.playerKey(), row.sourcePlayerId(), row.playerName(), row.avatarUrl(),
                teamNames, positions,
                matchCount, row.mvpCount(), row.mvpVotes(),
                row.totalKills(), row.totalAssists(), row.totalDeaths(),
                PlayerStatisticsMath.ratio(row.totalKills() + row.totalAssists(), row.totalDeaths()),
                PlayerStatisticsMath.perGame(row.totalKills(), matchCount),
                PlayerStatisticsMath.perGame(row.totalAssists(), matchCount),
                PlayerStatisticsMath.perGame(row.totalDeaths(), matchCount),
                row.weightedGoldPerGame(),
                row.weightedCreepScorePerGame(),
                row.weightedWardPlacedPerGame(),
                row.weightedWardKilledPerGame(),
                row.weightedKillParticipantPercent(),
                row.weightedGoldGapPerGame(),
                row.weightedDamagePercent(),
                row.weightedGoldPercent(),
                matchCount >= query.minimumMatchCount()
        );
    }

    private Comparator<PlayerStatistics> comparator(PlayerStatisticsQuery query) {
        Comparator<PlayerStatistics> comparator = switch (query.sortBy()) {
            case "totalKills" -> Comparator.comparingLong(PlayerStatistics::totalKills);
            case "mvpCount" -> Comparator.comparingLong(PlayerStatistics::mvpCount);
            case "killPerGame" -> Comparator.comparing(PlayerStatistics::killPerGame);
            case "goldPerGame" -> Comparator.comparing(PlayerStatistics::goldPerGame);
            case "damagePercent" -> Comparator.comparing(PlayerStatistics::damagePercent);
            case "matchCount" -> Comparator.comparingLong(PlayerStatistics::matchCount);
            default -> Comparator.comparing(PlayerStatistics::kda);
        };
        return query.sortDirection().apply(comparator).thenComparing(PlayerStatistics::playerName);
    }

    private List<PlayerStatistics> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取选手统计 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, List<PlayerStatistics> items) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(items), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入选手统计 Redis 缓存失败，key={}", key, exception);
        }
    }
}
