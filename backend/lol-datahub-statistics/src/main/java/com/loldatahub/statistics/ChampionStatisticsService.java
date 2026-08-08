package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionStatistics;
import com.loldatahub.domain.statistics.ChampionStatisticsQuery;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.model.ChampionAggregateRow;
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

@Service
public class ChampionStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(ChampionStatisticsService.class);
    private static final TypeReference<List<ChampionStatistics>> CACHE_TYPE = new TypeReference<>() { };

    private final ChampionStatisticsMapper mapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ChampionStatisticsService(ChampionStatisticsMapper mapper,
                                     StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.mapper = mapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public ChampionStatisticsResult query(ChampionStatisticsQuery query) {
        if (query.stageIds().isEmpty()) {
            throw new IllegalArgumentException("至少需要选择一个赛段");
        }
        List<Long> collectedStageIds = mapper.findCollectedStageIds(query.seasonId(), query.stageIds());
        List<Long> missingStageIds = query.stageIds().stream()
                .filter(stageId -> !collectedStageIds.contains(stageId))
                .toList();
        if (!missingStageIds.isEmpty()) {
            throw new IllegalArgumentException("以下赛段尚未采集英雄数据：" + missingStageIds);
        }
        long dataVersion = mapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:v" + dataVersion + ":champion:" + query.cacheFingerprint();
        List<ChampionStatistics> cached = readCache(cacheKey);
        if (cached != null) {
            return new ChampionStatisticsResult(dataVersion, query.minimumPickCount(), cached.size(), cached);
        }

        List<ChampionStatistics> items = new ArrayList<>(mapper.aggregateChampions(
                query.seasonId(), query.stageIds(), query.minimumPickCount()
        ).stream().map(row -> map(row, query.minimumPickCount())).toList());
        items.sort(comparator(query));
        writeCache(cacheKey, items);
        return new ChampionStatisticsResult(dataVersion, query.minimumPickCount(), items.size(), items);
    }

    private ChampionStatistics map(ChampionAggregateRow row, int minimumPickCount) {
        return new ChampionStatistics(
                row.championId(), row.championName(), row.championTitle(), row.championLogo(),
                parsePositions(row.positionsJson()), row.sampleBaseCount(), row.pickCount(), row.banCount(),
                row.bpCount(), row.winningCount(), row.totalKills(), row.totalDeaths(), row.totalAssists(),
                StatisticsMath.ratio(row.pickCount(), row.sampleBaseCount()),
                StatisticsMath.ratio(row.banCount(), row.sampleBaseCount()),
                StatisticsMath.ratio(row.bpCount(), row.sampleBaseCount()),
                StatisticsMath.ratio(row.winningCount(), row.pickCount()),
                StatisticsMath.ratio(row.totalKills() + row.totalAssists(), row.totalDeaths()),
                row.pickCount() >= minimumPickCount, row.sourceUpdatedAt()
        );
    }

    private Comparator<ChampionStatistics> comparator(ChampionStatisticsQuery query) {
        Comparator<ChampionStatistics> comparator = switch (query.sortBy()) {
            case "pickCount" -> Comparator.comparingLong(ChampionStatistics::pickCount);
            case "winningRate" -> Comparator.comparing(ChampionStatistics::winningRate);
            case "pickRate" -> Comparator.comparing(ChampionStatistics::pickRate);
            case "banRate" -> Comparator.comparing(ChampionStatistics::banRate);
            case "championName" -> Comparator.comparing(ChampionStatistics::championName);
            default -> Comparator.comparing(ChampionStatistics::bpRate);
        };
        return query.sortDirection().apply(comparator).thenComparing(ChampionStatistics::championName);
    }

    private List<String> parsePositions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private List<ChampionStatistics> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取英雄统计 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, List<ChampionStatistics> items) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(items), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入英雄统计 Redis 缓存失败，key={}", key, exception);
        }
    }
}
