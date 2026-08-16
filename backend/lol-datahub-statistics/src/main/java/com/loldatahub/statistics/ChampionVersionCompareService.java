package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.ChampionVersionCompareQuery;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.ChampionCatalogRow;
import com.loldatahub.infrastructure.model.ChampionSnapshotRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 版本窗口对比服务：每个窗口取各赛段内不晚于窗口日期的最近一次英雄快照，
 * 早于全部快照的窗口回退到最早快照，保证新英雄/新赛段也有可比基线。
 */
@Service
public class ChampionVersionCompareService {
    private static final Logger log = LoggerFactory.getLogger(ChampionVersionCompareService.class);
    private static final TypeReference<ChampionVersionCompareResult> CACHE_TYPE = new TypeReference<>() { };

    private final ChampionStatisticsMapper championStatisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public ChampionVersionCompareService(ChampionStatisticsMapper championStatisticsMapper,
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

    public ChampionVersionCompareResult query(ChampionVersionCompareQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s14:v" + dataVersion + ":version-compare:" + query.cacheFingerprint();
        ChampionVersionCompareResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<ChampionSnapshotRow> snapshots = championStatisticsMapper.findChampionSnapshots(query.stages());
        WindowTotals fromTotals = aggregateWindow(snapshots, query.fromDate());
        WindowTotals toTotals = aggregateWindow(snapshots, query.toDate());

        Map<Long, ChampionCatalogRow> catalog = new HashMap<>();
        for (ChampionCatalogRow row : championStatisticsMapper.findChampionCatalog()) {
            catalog.put(row.sourceChampionId(), row);
        }

        List<ChampionVersionCompareResult.Item> items = new ArrayList<>();
        for (long championId : unionChampionIds(fromTotals, toTotals)) {
            WindowTotals.StageTotals from = fromTotals.totals().get(championId);
            WindowTotals.StageTotals to = toTotals.totals().get(championId);
            long fromPick = from == null ? 0 : from.pickCount();
            long toPick = to == null ? 0 : to.pickCount();
            long fromWins = from == null ? 0 : from.winningCount();
            long toWins = to == null ? 0 : to.winningCount();
            // 窗口期间胜/负场：结束快照减起始快照；快照口径修正导致负数时钳制为 0
            long windowWins = Math.max(0, toWins - fromWins);
            long windowLosses = Math.max(0, (toPick - fromPick) - windowWins);
            BigDecimal fromRate = from == null ? null : winRate(from);
            BigDecimal toRate = to == null ? null : winRate(to);
            ChampionCatalogRow meta = catalog.get(championId);
            items.add(new ChampionVersionCompareResult.Item(
                    championId,
                    meta == null ? "英雄 #" + championId : meta.internalName(),
                    meta == null ? null : meta.chineseName(),
                    meta == null ? null : meta.logoUrl(),
                    fromPick, toPick, toPick - fromPick,
                    windowWins, windowLosses,
                    fromRate, toRate,
                    rateDelta(fromRate, toRate)));
        }
        items.sort(Comparator
                .comparingLong((ChampionVersionCompareResult.Item item) -> -item.pickDelta())
                .thenComparingLong(item -> -item.toPickCount())
                .thenComparingLong(ChampionVersionCompareResult.Item::championId));

        ChampionVersionCompareResult result = new ChampionVersionCompareResult(
                query.fromDate(), query.toDate(), items);
        writeCache(cacheKey, result);
        return result;
    }

    private static List<Long> unionChampionIds(WindowTotals left, WindowTotals right) {
        Map<Long, Boolean> ids = new LinkedHashMap<>();
        left.totals().keySet().forEach(id -> ids.put(id, true));
        right.totals().keySet().forEach(id -> ids.put(id, true));
        return List.copyOf(ids.keySet());
    }

    private static BigDecimal winRate(WindowTotals.StageTotals totals) {
        return totals.pickCount() <= 0
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(totals.winningCount())
                        .divide(BigDecimal.valueOf(totals.pickCount()), 6, RoundingMode.HALF_UP);
    }

    private static BigDecimal rateDelta(BigDecimal from, BigDecimal to) {
        BigDecimal fromValue = from == null ? BigDecimal.ZERO : from;
        BigDecimal toValue = to == null ? BigDecimal.ZERO : to;
        return toValue.subtract(fromValue).setScale(6, RoundingMode.HALF_UP);
    }

    private record StageSnapshotKey(long seasonId, long stageId, long championId) {
    }

    private record WindowTotals(Map<Long, StageTotals> totals) {
        private record StageTotals(long pickCount, long winningCount) {
        }
    }

    private static WindowTotals aggregateWindow(List<ChampionSnapshotRow> snapshots, LocalDate date) {
        LocalDateTime boundary = date.plusDays(1).atStartOfDay();
        Map<StageSnapshotKey, ChampionSnapshotRow> latestWithin = new LinkedHashMap<>();
        Map<StageSnapshotKey, ChampionSnapshotRow> earliest = new LinkedHashMap<>();
        for (ChampionSnapshotRow row : snapshots) {
            StageSnapshotKey key = new StageSnapshotKey(
                    row.sourceSeasonId(), row.sourceStageId(), row.sourceChampionId());
            ChampionSnapshotRow currentLatest = latestWithin.get(key);
            if (!row.collectedAt().isAfter(boundary)
                    && (currentLatest == null || row.collectedAt().isAfter(currentLatest.collectedAt()))) {
                latestWithin.put(key, row);
            }
            ChampionSnapshotRow currentEarliest = earliest.get(key);
            if (currentEarliest == null || row.collectedAt().isBefore(currentEarliest.collectedAt())) {
                earliest.put(key, row);
            }
        }
        Map<Long, WindowTotals.StageTotals> totals = new LinkedHashMap<>();
        for (Map.Entry<StageSnapshotKey, ChampionSnapshotRow> entry : earliest.entrySet()) {
            // 窗口日期早于该赛段全部快照时回退到最早快照，保证窗口之间口径可减
            ChampionSnapshotRow row = latestWithin.getOrDefault(entry.getKey(), entry.getValue());
            WindowTotals.StageTotals current = totals.get(row.sourceChampionId());
            totals.put(row.sourceChampionId(), new WindowTotals.StageTotals(
                    (current == null ? 0 : current.pickCount()) + row.pickCount(),
                    (current == null ? 0 : current.winningCount()) + row.winningCount()));
        }
        return new WindowTotals(totals);
    }

    private ChampionVersionCompareResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取版本对比 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, ChampionVersionCompareResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入版本对比 Redis 缓存失败，key={}", key, exception);
        }
    }
}
