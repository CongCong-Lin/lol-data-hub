package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamStatistics;
import com.loldatahub.domain.statistics.TeamStatisticsMath;
import com.loldatahub.domain.statistics.TeamStatisticsQuery;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamStatisticsMapper;
import com.loldatahub.infrastructure.model.TeamAggregateRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TeamStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(TeamStatisticsService.class);
    private static final TypeReference<List<TeamStatistics>> CACHE_TYPE = new TypeReference<>() { };

    private final TeamStatisticsMapper mapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public TeamStatisticsService(TeamStatisticsMapper mapper,
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

    public TeamStatisticsResult query(TeamStatisticsQuery query) {
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
            throw new IllegalArgumentException("以下赛段尚未采集战队数据：" + missingStr);
        }
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s6:v" + dataVersion + ":team:" + query.cacheFingerprint();
        List<TeamStatistics> cached = readCache(cacheKey);
        if (cached != null) {
            return new TeamStatisticsResult(dataVersion, query.minimumMatchCount(), cached.size(), cached);
        }

        List<TeamStatistics> items = new ArrayList<>(mapper.aggregateTeams(
                stages, query.minimumMatchCount()
        ).stream().map(row -> map(row, query.minimumMatchCount())).toList());
        items.sort(comparator(query));
        writeCache(cacheKey, items);
        return new TeamStatisticsResult(dataVersion, query.minimumMatchCount(), items.size(), items);
    }

    private TeamStatistics map(TeamAggregateRow row, int minimumMatchCount) {
        return new TeamStatistics(
                row.teamId(), row.teamName(), row.teamLogo(),
                row.matchCount(), row.gameCount(), row.matchWinCount(),
                TeamStatisticsMath.ratio(row.matchWinCount(), row.matchCount()),
                row.totalAssists() == null ? null
                        : TeamStatisticsMath.kda(row.totalKills(), row.totalAssists(), row.totalDeaths()),
                row.totalKills(),
                TeamStatisticsMath.ratio(row.totalKills(), row.gameCount()),
                row.totalDeaths(),
                TeamStatisticsMath.ratio(row.totalDeaths(), row.gameCount()),
                row.totalDamage() == null ? null
                        : row.totalDamage().divide(BigDecimal.valueOf(row.gameCount()), 6, java.math.RoundingMode.HALF_UP),
                perGame(row.totalGameSeconds(), row.gameCount()),
                perMinute(row.totalGold(), row.totalGameSeconds()),
                perMinute(row.totalWardsPlaced(), row.totalGameSeconds()),
                perMinute(row.totalWardsKilled(), row.totalGameSeconds()),
                ratio(row.totalDragons(), row.totalDragonOpportunities()),
                ratio(row.totalBarons(), row.totalBaronOpportunities()),
                ratio(row.firstBloodGames(), row.gameCount()),
                perMinute(row.totalDamage(), row.totalGameSeconds()),
                perMinute(row.totalMinionKills(), row.totalGameSeconds()),
                perGame(row.totalTurrets(), row.gameCount()),
                perGame(row.totalTurretsLost(), row.gameCount()),
                row.weightedWardPlacedPerGame(),
                row.weightedWardKilledPerGame(),
                row.weightedGoldPerGame(),
                row.weightedBaronKillPerGame(),
                row.weightedDrakeKillPerGame(),
                row.matchCount() >= minimumMatchCount
        );
    }

    private static BigDecimal perGame(Number total, long games) {
        if (total == null || games <= 0) return null;
        return new BigDecimal(total.toString()).divide(BigDecimal.valueOf(games), 6, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal perMinute(Number total, Long seconds) {
        if (total == null || seconds == null || seconds <= 0) return null;
        return new BigDecimal(total.toString()).multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(seconds), 6, java.math.RoundingMode.HALF_UP);
    }

    private static BigDecimal ratio(Long numerator, Long denominator) {
        if (numerator == null || denominator == null || denominator <= 0) return null;
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, java.math.RoundingMode.HALF_UP);
    }

    private Comparator<TeamStatistics> comparator(TeamStatisticsQuery query) {
        Comparator<TeamStatistics> comparator = switch (query.sortBy()) {
            case "teamName" -> Comparator.comparing(TeamStatistics::teamName);
            case "matchCount" -> Comparator.comparingLong(TeamStatistics::matchCount);
            case "gameCount" -> Comparator.comparingLong(TeamStatistics::gameCount);
            case "matchWinCount" -> Comparator.comparingLong(TeamStatistics::matchWinCount);
            case "winningRate" -> Comparator.comparing(TeamStatistics::winningRate);
            case "kda" -> nullableDecimalComparator(TeamStatistics::kda);
            case "totalKills" -> Comparator.comparingLong(TeamStatistics::totalKills);
            case "killPerGame" -> Comparator.comparing(TeamStatistics::killPerGame);
            case "deathPerGame" -> Comparator.comparing(TeamStatistics::deathPerGame);
            case "wardPlacedPerGame" -> Comparator.comparing(TeamStatistics::wardPlacedPerGame);
            case "wardKilledPerGame" -> Comparator.comparing(TeamStatistics::wardKilledPerGame);
            case "goldPerGame" -> Comparator.comparing(TeamStatistics::goldPerGame);
            case "damagePerGame" -> nullableDecimalComparator(TeamStatistics::damagePerGame);
            case "averageGameDurationSeconds" -> nullableDecimalComparator(TeamStatistics::averageGameDurationSeconds);
            case "goldPerMinute" -> nullableDecimalComparator(TeamStatistics::goldPerMinute);
            case "wardPlacedPerMinute" -> nullableDecimalComparator(TeamStatistics::wardPlacedPerMinute);
            case "wardKilledPerMinute" -> nullableDecimalComparator(TeamStatistics::wardKilledPerMinute);
            case "drakeControlRate" -> nullableDecimalComparator(TeamStatistics::drakeControlRate);
            case "baronControlRate" -> nullableDecimalComparator(TeamStatistics::baronControlRate);
            case "firstBloodRate" -> nullableDecimalComparator(TeamStatistics::firstBloodRate);
            case "damagePerMinute" -> nullableDecimalComparator(TeamStatistics::damagePerMinute);
            case "creepScorePerMinute" -> nullableDecimalComparator(TeamStatistics::creepScorePerMinute);
            case "turretKillPerGame" -> nullableDecimalComparator(TeamStatistics::turretKillPerGame);
            case "turretLostPerGame" -> nullableDecimalComparator(TeamStatistics::turretLostPerGame);
            case "baronKillPerGame" -> Comparator.comparing(TeamStatistics::baronKillPerGame);
            case "drakeKillPerGame" -> Comparator.comparing(TeamStatistics::drakeKillPerGame);
            default -> throw new IllegalStateException("未实现的战队排序字段：" + query.sortBy());
        };
        return query.sortDirection().apply(comparator).thenComparing(TeamStatistics::teamName);
    }

    private static Comparator<TeamStatistics> nullableDecimalComparator(
            java.util.function.Function<TeamStatistics, BigDecimal> extractor) {
        return Comparator.comparing(extractor, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private List<TeamStatistics> readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取战队统计 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, List<TeamStatistics> items) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(items), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入战队统计 Redis 缓存失败，key={}", key, exception);
        }
    }
}
