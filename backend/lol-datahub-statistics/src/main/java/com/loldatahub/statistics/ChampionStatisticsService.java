package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionStatistics;
import com.loldatahub.domain.statistics.ChampionStatisticsQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionAggregateRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChampionStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(ChampionStatisticsService.class);
    private static final TypeReference<List<ChampionStatistics>> CACHE_TYPE = new TypeReference<>() { };

    private final ChampionStatisticsMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ChampionStatisticsService(ChampionStatisticsMapper mapper,
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

    public ChampionStatisticsResult query(ChampionStatisticsQuery query) {
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
            throw new IllegalArgumentException("以下赛段尚未采集英雄数据：" + missingStr);
        }
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s6:v" + dataVersion + ":champion:" + query.cacheFingerprint();
        List<ChampionStatistics> cached = readCache(cacheKey);
        if (cached != null) {
            return new ChampionStatisticsResult(dataVersion, query.minimumPickCount(), cached.size(), cached);
        }

        List<ChampionStatistics> items = new ArrayList<>(mapper.aggregateChampions(
                stages, query.minimumPickCount(), query.position()
        ).stream().map(row -> map(row, query.minimumPickCount())).toList());
        items.sort(comparator(query));
        writeCache(cacheKey, items);
        return new ChampionStatisticsResult(dataVersion, query.minimumPickCount(), items.size(), items);
    }

    private ChampionStatistics map(ChampionAggregateRow row, int minimumPickCount) {
        long pickCount = row.pickCount();
        return new ChampionStatistics(
                row.championId(), row.championName(), row.championTitle(), row.championLogo(),
                mergePositionsFromCsv(row.positionsCsv()), row.sampleBaseCount(), pickCount, row.banCount(),
                row.bpCount(), row.winningCount(), row.totalKills(), row.totalDeaths(), row.totalAssists(),
                StatisticsMath.ratio(pickCount, row.sampleBaseCount()),
                StatisticsMath.ratio(row.banCount(), row.sampleBaseCount()),
                StatisticsMath.ratio(row.bpCount(), row.sampleBaseCount()),
                StatisticsMath.ratio(row.winningCount(), pickCount),
                StatisticsMath.kda(row.totalKills(), row.totalAssists(), row.totalDeaths()),
                StatisticsMath.perGame(row.totalKills(), pickCount),
                StatisticsMath.perGame(row.totalAssists(), pickCount),
                StatisticsMath.perGame(row.totalDeaths(), pickCount),
                splitCsv(row.mostUsedPlayersCsv()),
                pickCount >= minimumPickCount, row.sourceUpdatedAt()
        );
    }

    private Comparator<ChampionStatistics> comparator(ChampionStatisticsQuery query) {
        Comparator<ChampionStatistics> comparator = switch (query.sortBy()) {
            case "championName" -> Comparator.comparing(ChampionStatistics::championName);
            case "positions" -> Comparator.comparing(value -> String.join("/", value.positions()));
            case "pickCount" -> Comparator.comparingLong(ChampionStatistics::pickCount);
            case "pickRate" -> Comparator.comparing(ChampionStatistics::pickRate);
            case "banCount" -> Comparator.comparingLong(ChampionStatistics::banCount);
            case "banRate" -> Comparator.comparing(ChampionStatistics::banRate);
            case "bpRate" -> Comparator.comparing(ChampionStatistics::bpRate);
            case "winningCount" -> Comparator.comparingLong(ChampionStatistics::winningCount);
            case "winningRate" -> Comparator.comparing(ChampionStatistics::winningRate);
            case "totalKills" -> Comparator.comparingLong(ChampionStatistics::totalKills);
            case "killPerGame" -> Comparator.comparing(ChampionStatistics::killPerGame);
            case "totalAssists" -> Comparator.comparingLong(ChampionStatistics::totalAssists);
            case "assistPerGame" -> Comparator.comparing(ChampionStatistics::assistPerGame);
            case "totalDeaths" -> Comparator.comparingLong(ChampionStatistics::totalDeaths);
            case "deathPerGame" -> Comparator.comparing(ChampionStatistics::deathPerGame);
            case "kda" -> Comparator.comparing(ChampionStatistics::kda);
            case "mostUsedPlayers" -> Comparator.comparing(value -> String.join("/", value.mostUsedPlayers()));
            default -> throw new IllegalStateException("未实现的英雄排序字段：" + query.sortBy());
        };
        Comparator<ChampionStatistics> ordered = query.sortDirection().apply(comparator);
        if ("winningRate".equals(query.sortBy())) {
            ordered = ordered.thenComparing(
                    Comparator.comparingLong(ChampionStatistics::pickCount).reversed()
            );
        }
        return ordered.thenComparing(ChampionStatistics::championName)
                .thenComparingLong(ChampionStatistics::championId);
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

    List<String> mergePositionsFromCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        if (!csv.contains("[") && !csv.contains("|")) {
            Set<String> values = Arrays.stream(csv.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
            return List.of("TOP", "JUN", "MID", "BOT", "SUP").stream()
                    .filter(values::contains)
                    .toList();
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        for (String segment : csv.split("\\|")) {
            List<String> parsed = parsePositions(segment.trim());
            merged.addAll(parsed);
        }
        return List.copyOf(merged);
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toUnmodifiableList());
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
