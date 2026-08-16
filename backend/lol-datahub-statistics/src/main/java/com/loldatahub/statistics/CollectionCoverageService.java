package com.loldatahub.statistics;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.infrastructure.mapper.CollectionCoverageMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.StageCatalogRow;
import com.loldatahub.infrastructure.model.StageGameCountRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 采集覆盖矩阵服务：合并四类 current 表的已采集赛段，标注每类覆盖与缺口。
 */
@Service
public class CollectionCoverageService {
    private static final Logger log = LoggerFactory.getLogger(CollectionCoverageService.class);
    private static final TypeReference<CollectionCoverageResult> CACHE_TYPE = new TypeReference<>() { };

    private final CollectionCoverageMapper coverageMapper;
    private final CatalogMapper catalogMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public CollectionCoverageService(CollectionCoverageMapper coverageMapper,
                                     CatalogMapper catalogMapper,
                                     SystemStateMapper systemStateMapper,
                                     StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.coverageMapper = coverageMapper;
        this.catalogMapper = catalogMapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public CollectionCoverageResult query() {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s15:v" + dataVersion + ":collection-coverage:all";
        try {
            String json = redisTemplate.opsForValue().get(cacheKey);
            if (json != null) {
                return objectMapper.readValue(json, CACHE_TYPE);
            }
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取采集覆盖 Redis 缓存失败，key={}", cacheKey, exception);
        }

        Set<StageKey> hero = new HashSet<>(coverageMapper.findHeroCollectedStages());
        Set<StageKey> team = new HashSet<>(coverageMapper.findTeamCollectedStages());
        Set<StageKey> player = new HashSet<>(coverageMapper.findPlayerCollectedStages());
        Map<StageKey, Long> gameCounts = coverageMapper.countMatchGamesByStage().stream()
                .collect(Collectors.toMap(
                        row -> new StageKey(row.sourceSeasonId(), row.sourceStageId()),
                        StageGameCountRow::games));

        TreeSet<StageKey> universe = new TreeSet<>();
        universe.addAll(hero);
        universe.addAll(team);
        universe.addAll(player);
        universe.addAll(gameCounts.keySet());

        Map<StageKey, StageCatalogRow> catalog = catalogMapper.findStageCatalogRows(List.copyOf(universe))
                .stream()
                .collect(Collectors.toMap(
                        row -> new StageKey(row.sourceSeasonId(), row.sourceStageId()),
                        row -> row));

        List<CollectionCoverageResult.StageCoverage> stages = new ArrayList<>();
        for (StageKey key : universe.descendingSet()) {
            StageCatalogRow meta = catalog.get(key);
            stages.add(new CollectionCoverageResult.StageCoverage(
                    key.sourceSeasonId(),
                    key.sourceStageId(),
                    meta == null ? "赛季 #" + key.sourceSeasonId() : meta.seasonName(),
                    meta == null ? "赛段 #" + key.sourceStageId() : meta.stageName(),
                    hero.contains(key),
                    team.contains(key),
                    player.contains(key),
                    gameCounts.getOrDefault(key, 0L)));
        }

        CollectionCoverageResult result = new CollectionCoverageResult(List.copyOf(stages));
        try {
            redisTemplate.opsForValue()
                    .set(cacheKey, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入采集覆盖 Redis 缓存失败，key={}", cacheKey, exception);
        }
        return result;
    }
}
