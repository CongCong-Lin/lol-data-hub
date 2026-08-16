package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionCounterQuery;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionCounterRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.List;

/**
 * 英雄对位克制服务。
 */
@Service
public class ChampionCounterStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(ChampionCounterStatisticsService.class);
    private static final TypeReference<ChampionCounterResult> CACHE_TYPE = new TypeReference<>() { };

    private final ChampionStatisticsMapper championStatisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ChampionCounterStatisticsService(ChampionStatisticsMapper championStatisticsMapper,
                                            SystemStateMapper systemStateMapper,
                                            StringRedisTemplate redisTemplate,
                                            ObjectMapper objectMapper,
                                            @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.championStatisticsMapper = championStatisticsMapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public ChampionCounterResult query(ChampionCounterQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s12:v" + dataVersion + ":champion-counter:" + query.cacheFingerprint();
        ChampionCounterResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ChampionCounterRow> rows = championStatisticsMapper.aggregateChampionCounters(
                query.stages(), query.championId(), query.position(), query.minimumGames());
        long totalGames = rows.stream().mapToLong(ChampionCounterRow::games).sum();
        List<ChampionCounterResult.Opponent> opponents = rows.stream()
                .map(row -> new ChampionCounterResult.Opponent(
                        row.opponentChampionId(), row.championName(), row.championChineseName(),
                        row.championTitle(), row.championLogo(), row.games(), row.wins(),
                        BigDecimal.valueOf(row.wins())
                                .divide(BigDecimal.valueOf(row.games()), 6, RoundingMode.HALF_UP)))
                .toList();

        ChampionCounterResult result =
                new ChampionCounterResult(query.championId(), query.position(), totalGames, opponents);
        writeCache(cacheKey, result);
        return result;
    }

    private ChampionCounterResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取英雄克制 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, ChampionCounterResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入英雄克制 Redis 缓存失败，key={}", key, exception);
        }
    }
}
