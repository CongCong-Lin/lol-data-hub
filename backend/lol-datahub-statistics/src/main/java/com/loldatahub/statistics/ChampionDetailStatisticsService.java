package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionDetailNotFoundException;
import com.loldatahub.domain.statistics.ChampionDetailProfile;
import com.loldatahub.domain.statistics.ChampionDetailQuery;
import com.loldatahub.domain.statistics.ChampionDetailStatisticsResult;
import com.loldatahub.domain.statistics.ChampionPlayerUsage;
import com.loldatahub.domain.statistics.ChampionPositionStat;
import com.loldatahub.domain.statistics.ChampionStatistics;
import com.loldatahub.domain.statistics.ChampionStatisticsQuery;
import com.loldatahub.domain.statistics.ChampionTrendPoint;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 英雄详情页统计服务。
 * 整体指标复用英雄统计列表的聚合与缓存，分路统计/常用选手榜/趋势来自独立查询。
 * position 非空时整体指标与常用选手榜都限定在该分路，分路统计与趋势始终展示全部位置。
 */
@Service
public class ChampionDetailStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(ChampionDetailStatisticsService.class);
    private static final TypeReference<ChampionDetailStatisticsResult> CACHE_TYPE = new TypeReference<>() { };

    private final ChampionStatisticsService championStatisticsService;
    private final ChampionStatisticsMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ChampionDetailStatisticsService(ChampionStatisticsService championStatisticsService,
                                           ChampionStatisticsMapper mapper,
                                           SystemStateMapper systemStateMapper,
                                           StringRedisTemplate redisTemplate,
                                           ObjectMapper objectMapper,
                                           @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.championStatisticsService = championStatisticsService;
        this.mapper = mapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public ChampionDetailStatisticsResult query(ChampionDetailQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s8:v" + dataVersion + ":champion-detail:" + query.cacheFingerprint();
        ChampionDetailStatisticsResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 复用英雄统计列表聚合：与列表页相同的公式、门槛与位置过滤口径
        List<ChampionStatistics> cohort = championStatisticsService.query(new ChampionStatisticsQuery(
                query.stages(), query.minimumPickCount(), query.position(), "championName", SortDirection.ASC)).items();
        ChampionStatistics overall = cohort.stream()
                .filter(champion -> champion.championId() == query.sourceChampionId())
                .findFirst()
                .orElseThrow(() -> new ChampionDetailNotFoundException(
                        "所选赛段内不存在英雄 " + query.sourceChampionId() + " 的数据"
                                + "（或未达到最低 " + query.minimumPickCount() + " 场的样本要求）"));

        com.loldatahub.infrastructure.model.ChampionProfileRow profileRow =
                mapper.findChampionProfile(query.sourceChampionId());
        if (profileRow == null) {
            throw new ChampionDetailNotFoundException("英雄 " + query.sourceChampionId() + " 不存在");
        }

        List<ChampionPositionStat> positionStats = positionStats(query, overall);
        List<ChampionPlayerUsage> topPlayers = mapper.aggregatePlayerUsage(
                        query.stages(), query.sourceChampionId(), query.position(), query.minimumPickCount())
                .stream().map(row -> new ChampionPlayerUsage(
                        row.sourcePlayerId(), row.playerName(), row.playerAvatar(), row.position(),
                        row.pickCount(), row.winningCount(),
                        StatisticsMath.ratio(row.winningCount(), row.pickCount()),
                        StatisticsMath.kda(row.totalKills(), row.totalAssists(), row.totalDeaths())))
                .toList();
        List<ChampionTrendPoint> trends = mapper.findChampionTrends(query.stages(), query.sourceChampionId())
                .stream().map(row -> new ChampionTrendPoint(
                        row.sourceSeasonId(), row.sourceStageId(), row.stageName(),
                        row.pickCount(), row.banCount(), row.winningCount(),
                        row.pickRate(), row.banRate(), row.winningRate()))
                .toList();

        ChampionDetailStatisticsResult result = new ChampionDetailStatisticsResult(
                dataVersion,
                query.minimumPickCount(),
                query.position(),
                new ChampionDetailProfile(
                        query.sourceChampionId(), profileRow.internalName(), profileRow.chineseName(),
                        profileRow.chineseTitle(), profileRow.logoUrl(), overall.positions()),
                overall,
                positionStats,
                topPlayers,
                trends,
                mapper.findLatestCollectedAt(query.stages()));
        writeCache(cacheKey, result);
        return result;
    }

    private List<ChampionPositionStat> positionStats(ChampionDetailQuery query, ChampionStatistics overall) {
        List<ChampionPositionStat> stats = mapper.aggregatePositionStats(query.stages(), query.sourceChampionId())
                .stream().map(row -> new ChampionPositionStat(
                        row.position(), row.pickCount(), row.winningCount(),
                        StatisticsMath.ratio(row.pickCount(), overall.pickCount()),
                        StatisticsMath.ratio(row.winningCount(), row.pickCount()),
                        StatisticsMath.kda(row.totalKills(), row.totalAssists(), row.totalDeaths())))
                .toList();
        // 与英雄列表同口径：所选赛段存在明细但未达到整体门槛时，分路统计仍按聚合表如实展示
        return stats;
    }

    private ChampionDetailStatisticsResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取英雄详情 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, ChampionDetailStatisticsResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入英雄详情 Redis 缓存失败，key={}", key, exception);
        }
    }
}
