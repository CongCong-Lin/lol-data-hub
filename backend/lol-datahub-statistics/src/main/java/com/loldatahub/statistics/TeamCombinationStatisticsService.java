package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.domain.statistics.TeamCombinationStatistics;
import com.loldatahub.domain.statistics.TeamCombinationStatisticsQuery;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamCombinationStatisticsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TeamCombinationStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(TeamCombinationStatisticsService.class);
    private static final TypeReference<List<TeamCombinationStatistics>> CACHE_TYPE = new TypeReference<>() { };

    private final TeamCombinationStatisticsMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public TeamCombinationStatisticsService(
            TeamCombinationStatisticsMapper mapper,
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

    public TeamCombinationStatisticsResult query(TeamCombinationStatisticsQuery query) {
        var collected = new HashSet<>(mapper.findCollectedStageKeys(query.stages()));
        List<StageKey> missing = query.stages().stream().filter(stage -> !collected.contains(stage)).toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("以下赛段尚未采集单局阵容数据：" + missing.stream()
                    .map(StageKey::canonical).collect(Collectors.joining(", ")));
        }

        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s5:v" + dataVersion + ":team-combination:" + query.cacheFingerprint();
        List<TeamCombinationStatistics> cached = readCache(cacheKey);
        if (cached != null) {
            return result(dataVersion, query, cached);
        }

        List<TeamCombinationStatistics> items = new ArrayList<>(mapper.aggregate(
                query.stages(), query.combinationType().name(), query.minimumPickCount()
        ).stream().map(row -> new TeamCombinationStatistics(
                row.teamId(), row.teamName(), row.teamLogo(), query.combinationType(),
                query.combinationType().firstPosition(), row.firstChampionId(), row.firstChampionName(),
                row.firstChampionTitle(), row.firstChampionLogo(), query.combinationType().secondPosition(),
                row.secondChampionId(), row.secondChampionName(), row.secondChampionTitle(),
                row.secondChampionLogo(), row.pickCount(), row.validGameCount(),
                StatisticsMath.ratio(row.pickCount(), row.validGameCount()), row.winningCount(),
                StatisticsMath.ratio(row.winningCount(), row.pickCount()),
                row.pickCount() >= query.minimumPickCount()
        )).toList());
        items.sort(comparator(query));
        writeCache(cacheKey, items);
        return result(dataVersion, query, items);
    }

    private TeamCombinationStatisticsResult result(long version, TeamCombinationStatisticsQuery query,
                                                    List<TeamCombinationStatistics> items) {
        return new TeamCombinationStatisticsResult(
                version, query.combinationType(), query.minimumPickCount(), items.size(), items);
    }

    private Comparator<TeamCombinationStatistics> comparator(TeamCombinationStatisticsQuery query) {
        Comparator<TeamCombinationStatistics> comparator = switch (query.sortBy()) {
            case "teamName" -> Comparator.comparing(TeamCombinationStatistics::teamName);
            case "firstChampionName" -> Comparator.comparing(TeamCombinationStatistics::firstChampionName);
            case "secondChampionName" -> Comparator.comparing(TeamCombinationStatistics::secondChampionName);
            case "pickCount" -> Comparator.comparingLong(TeamCombinationStatistics::pickCount);
            case "validGameCount" -> Comparator.comparingLong(TeamCombinationStatistics::validGameCount);
            case "pickRate" -> Comparator.comparing(TeamCombinationStatistics::pickRate);
            case "winningCount" -> Comparator.comparingLong(TeamCombinationStatistics::winningCount);
            case "winningRate" -> Comparator.comparing(TeamCombinationStatistics::winningRate);
            default -> throw new IllegalStateException("未实现的组合排序字段：" + query.sortBy());
        };
        return query.sortDirection().apply(comparator)
                .thenComparing(Comparator.comparingLong(TeamCombinationStatistics::pickCount).reversed())
                .thenComparing(TeamCombinationStatistics::teamName)
                .thenComparing(TeamCombinationStatistics::firstChampionName)
                .thenComparing(TeamCombinationStatistics::secondChampionName);
    }

    private List<TeamCombinationStatistics> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取英雄组合统计 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, List<TeamCombinationStatistics> items) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(items), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入英雄组合统计 Redis 缓存失败，key={}", key, exception);
        }
    }
}
