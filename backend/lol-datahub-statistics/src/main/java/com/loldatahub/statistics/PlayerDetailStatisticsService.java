package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerDetailNotFoundException;
import com.loldatahub.domain.statistics.PlayerDetailProfile;
import com.loldatahub.domain.statistics.PlayerDetailQuery;
import com.loldatahub.domain.statistics.PlayerAverageContrastMetric;
import com.loldatahub.domain.statistics.PlayerHeroUsage;
import com.loldatahub.domain.statistics.PlayerRadarMetric;
import com.loldatahub.domain.statistics.PlayerStatistics;
import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.RankedPlayerMetric;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.PlayerHeroUsageMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerHeroUsageAggregateRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * 选手详情页统计服务。
 * 排名、同位置平均、雷达归一化与英雄聚合全部由后端统一完成，前端不做二次计算。
 */
@Service
public class PlayerDetailStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(PlayerDetailStatisticsService.class);
    private static final TypeReference<PlayerDetailStatisticsResult> CACHE_TYPE = new TypeReference<>() { };
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal RADAR_SCORE_FLOOR = BigDecimal.TEN;
    private static final BigDecimal RADAR_NEUTRAL_SCORE = BigDecimal.valueOf(55);
    private static final Set<String> PERCENT_METRICS = Set.of(
            "killParticipantPercent", "damagePercent", "goldPercent");

    private record MetricDefinition(String key, String label, boolean higherIsBetter,
                                    Function<PlayerStatistics, BigDecimal> value, boolean integerDisplay) {
    }

    /** 核心指标口径与选手统计列表完全一致（复用 PlayerStatisticsService 的聚合结果）。 */
    private static final List<MetricDefinition> CORE_METRICS = List.of(
            new MetricDefinition("matchCount", "系列赛数", true, p -> BigDecimal.valueOf(p.matchCount()), true),
            new MetricDefinition("gameCount", "小局数", true, p -> BigDecimal.valueOf(p.gameCount()), true),
            new MetricDefinition("mvpCount", "MVP 次数", true, p -> BigDecimal.valueOf(p.mvpCount()), true),
            new MetricDefinition("kda", "KDA", true, PlayerStatistics::kda, false),
            new MetricDefinition("killPerGame", "场均击杀", true, PlayerStatistics::killPerGame, false),
            new MetricDefinition("deathPerGame", "场均死亡", false, PlayerStatistics::deathPerGame, false),
            new MetricDefinition("assistPerGame", "场均助攻", true, PlayerStatistics::assistPerGame, false),
            new MetricDefinition("goldPerGame", "场均经济", true, PlayerStatistics::goldPerGame, false),
            new MetricDefinition("creepScorePerGame", "场均补刀", true, PlayerStatistics::creepScorePerGame, false),
            new MetricDefinition("wardPlacedPerGame", "场均插眼", true, PlayerStatistics::wardPlacedPerGame, false),
            new MetricDefinition("wardKilledPerGame", "场均排眼", true, PlayerStatistics::wardKilledPerGame, false),
            new MetricDefinition("killParticipantPercent", "参团率", true, PlayerStatistics::killParticipantPercent, false),
            new MetricDefinition("goldGapPerGame", "场均经济差", true, PlayerStatistics::goldGapPerGame, false),
            new MetricDefinition("damagePercent", "伤害占比", true, PlayerStatistics::damagePercent, false),
            new MetricDefinition("goldPercent", "经济占比", true, PlayerStatistics::goldPercent, false)
    );

    /** 八维雷达使用统一指标；评分与排名始终只在当前所选位置的选手中计算。 */
    private static final List<MetricDefinition> RADAR_METRICS = List.of(
            metric("kda", "KDA", PlayerStatistics::kda),
            metric("killParticipantPercent", "参团率", PlayerStatistics::killParticipantPercent),
            metric("creepScorePerGame", "场均补刀", PlayerStatistics::creepScorePerGame),
            metric("goldGapPerGame", "场均经济差", PlayerStatistics::goldGapPerGame),
            metric("killPerGame", "场均击杀", PlayerStatistics::killPerGame),
            metric("damagePercent", "伤害占比", PlayerStatistics::damagePercent),
            metric("damagePerGame", "伤害", PlayerStatistics::damagePerGame),
            new MetricDefinition("deathPerGame", "场均死亡", false, PlayerStatistics::deathPerGame, false)
    );

    /**
     * 职业场均对比使用当前数据源实际提供的七项指标。
     * 数据源没有逐局伤害/承伤绝对值，因此这里使用可比且有明确口径的伤害占比、经济占比，
     * 不把占比伪装成不存在的场均伤害或场均承伤。
     */
    private static final List<MetricDefinition> AVERAGE_CONTRAST_METRICS = List.of(
            metric("killPerGame", "击杀", PlayerStatistics::killPerGame),
            new MetricDefinition("deathPerGame", "死亡", false, PlayerStatistics::deathPerGame, false),
            metric("assistPerGame", "助攻", PlayerStatistics::assistPerGame),
            metric("creepScorePerGame", "补刀", PlayerStatistics::creepScorePerGame),
            metric("damagePerGame", "伤害", PlayerStatistics::damagePerGame),
            new MetricDefinition("damagePercent", "伤害占比", true, PlayerStatistics::damagePercent, false),
            metric("goldPerGame", "经济", PlayerStatistics::goldPerGame)
    );

    private static MetricDefinition metric(String key, String label, Function<PlayerStatistics, BigDecimal> value) {
        return new MetricDefinition(key, label, true, value, false);
    }

    private final PlayerStatisticsService playerStatisticsService;
    private final PlayerHeroUsageMapper heroUsageMapper;
    private final ChampionStatisticsMapper championStatisticsMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public PlayerDetailStatisticsService(PlayerStatisticsService playerStatisticsService,
                                         PlayerHeroUsageMapper heroUsageMapper,
                                         ChampionStatisticsMapper championStatisticsMapper,
                                         SystemStateMapper systemStateMapper,
                                         StringRedisTemplate redisTemplate,
                                         ObjectMapper objectMapper,
                                         @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.playerStatisticsService = playerStatisticsService;
        this.heroUsageMapper = heroUsageMapper;
        this.championStatisticsMapper = championStatisticsMapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public PlayerDetailStatisticsResult query(PlayerDetailQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s10:v" + dataVersion + ":player-detail:" + query.cacheFingerprint();
        PlayerDetailStatisticsResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 复用现有选手统计聚合：与选手列表相同的公式、门槛与加权口径
        PlayerStatisticsResult cohortResult = playerStatisticsService.query(new PlayerStatisticsQuery(
                query.stages(), query.minimumMatchCount(), query.position(), "playerName", SortDirection.ASC));
        List<PlayerStatistics> cohort = cohortResult.items();

        PlayerStatistics target = cohort.stream()
                .filter(player -> Long.valueOf(query.sourcePlayerId()).equals(player.sourcePlayerId()))
                .findFirst()
                .orElseThrow(() -> notFound(query));

        List<String> availablePositions = heroUsageMapper.findQualifiedPlayerPositions(
                query.stages(), query.sourcePlayerId(), query.minimumMatchCount());
        if (!availablePositions.contains(query.position())) {
            availablePositions = List.of(query.position());
        }

        PlayerDetailStatisticsResult result = new PlayerDetailStatisticsResult(
                dataVersion,
                query.minimumMatchCount(),
                query.position(),
                cohort.size(),
                new PlayerDetailProfile(target.sourcePlayerId(), target.playerName(), target.playerAvatar(),
                        target.teamNames(), availablePositions, target.matchCount(), target.gameCount()),
                coreMetrics(target, cohort),
                radarMetrics(query.position(), target, cohort),
                true,
                List.of(),
                0L,
                List.of(),
                heroUsageMapper.findLatestCollectedAt(query.stages()),
                averageContrastMetrics(target, cohort)
        );
        result = withHeroUsage(result, query);
        writeCache(cacheKey, result);
        return result;
    }

    private PlayerDetailStatisticsResult withHeroUsage(PlayerDetailStatisticsResult base, PlayerDetailQuery query) {
        List<StageKey> stages = query.stages();
        HashSet<StageKey> collected = new HashSet<>(championStatisticsMapper.findCollectedStageKeys(stages));
        List<String> missing = stages.stream()
                .filter(stage -> !collected.contains(stage))
                .map(StageKey::canonical)
                .toList();
        if (!missing.isEmpty()) {
            return new PlayerDetailStatisticsResult(
                    base.dataVersion(), base.minimumMatchCount(), base.position(), base.cohortSize(),
                    base.player(), base.coreMetrics(), base.radarMetrics(),
                    false, missing, 0L, List.of(), base.latestCollectedAt(), base.averageContrastMetrics());
        }

        List<PlayerHeroUsageAggregateRow> aggregateRows = heroUsageMapper.aggregateHeroUsage(
                stages, query.sourcePlayerId(), query.heroPosition());
        long totalGames = aggregateRows.stream().mapToLong(PlayerHeroUsageAggregateRow::pickCount).sum();
        List<PlayerHeroUsage> heroes = aggregateRows.stream()
                .map(row -> new PlayerHeroUsage(
                        row.sourceChampionId(), row.championName(), row.championChineseName(),
                        row.championTitle(), row.championLogo(),
                        row.pickCount(), StatisticsMath.ratio(row.pickCount(), totalGames),
                        row.winningCount(), StatisticsMath.ratio(row.winningCount(), row.pickCount()),
                        row.totalKills(), row.totalDeaths(), row.totalAssists(),
                        StatisticsMath.kda(row.totalKills(), row.totalAssists(), row.totalDeaths()),
                        StatisticsMath.perGame(row.totalKills(), row.pickCount()),
                        StatisticsMath.perGame(row.totalDeaths(), row.pickCount()),
                        StatisticsMath.perGame(row.totalAssists(), row.pickCount())))
                .sorted(Comparator.comparingLong(PlayerHeroUsage::pickCount).reversed()
                        .thenComparing(Comparator.comparing(PlayerHeroUsage::winningRate).reversed())
                        .thenComparing(PlayerHeroUsage::championChineseName)
                        .thenComparingLong(PlayerHeroUsage::sourceChampionId))
                .toList();
        return new PlayerDetailStatisticsResult(
                base.dataVersion(), base.minimumMatchCount(), base.position(), base.cohortSize(),
                base.player(), base.coreMetrics(), base.radarMetrics(),
                true, List.of(), totalGames, heroes, base.latestCollectedAt(), base.averageContrastMetrics());
    }

    private PlayerDetailNotFoundException notFound(PlayerDetailQuery query) {
        if (heroUsageMapper.countPlayersBySourceId(query.sourcePlayerId()) == 0) {
            return new PlayerDetailNotFoundException("选手 " + query.sourcePlayerId() + " 不存在");
        }
        if (heroUsageMapper.countPlayerPositionRows(query.stages(), query.sourcePlayerId(), query.position()) == 0) {
            return new PlayerDetailNotFoundException(
                    "选手 " + query.sourcePlayerId() + " 在所选赛段中没有 " + query.position() + " 位置数据");
        }
        return new PlayerDetailNotFoundException(
                "该选手在当前筛选范围内未达到最低 " + query.minimumMatchCount() + " 场的样本要求");
    }

    private List<RankedPlayerMetric> coreMetrics(PlayerStatistics target, List<PlayerStatistics> cohort) {
        return CORE_METRICS.stream()
                .map(definition -> {
                    BigDecimal value = definition.value().apply(target);
                    int rank = rank(value, definition.higherIsBetter(), definition.value(), cohort);
                    String formatted = formatMetricValue(definition, value);
                    return new RankedPlayerMetric(definition.key(), definition.label(), value,
                            formatted, rank, cohort.size(), definition.higherIsBetter());
                })
                .toList();
    }

    private String formatMetricValue(MetricDefinition definition, BigDecimal value) {
        if (definition.integerDisplay()) {
            return String.valueOf(value.longValue());
        }
        if (PERCENT_METRICS.contains(definition.key())) {
            return value.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
        }
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private List<PlayerRadarMetric> radarMetrics(String position, PlayerStatistics target,
                                                 List<PlayerStatistics> cohort) {
        return RADAR_METRICS.stream()
                .map(definition -> {
                    List<PlayerStatistics> comparablePlayers = cohort.stream()
                            .filter(player -> definition.value().apply(player) != null)
                            .toList();
                    List<BigDecimal> values = comparablePlayers.stream()
                            .map(definition.value())
                            .toList();
                    BigDecimal value = definition.value().apply(target);
                    BigDecimal averageValue = average(values);
                    boolean available = value != null && !values.isEmpty();
                    BigDecimal playerScore = available
                            ? robustRadarScore(value, definition.higherIsBetter(), values)
                            : BigDecimal.ZERO;
                    BigDecimal averageScore = values.isEmpty()
                            ? BigDecimal.ZERO
                            : robustRadarScore(averageValue, definition.higherIsBetter(), values);
                    int rank = available
                            ? rank(value, definition.higherIsBetter(), definition.value(), comparablePlayers)
                            : 0;
                    return new PlayerRadarMetric(definition.key(), definition.label(), value, averageValue,
                            playerScore, averageScore, rank, comparablePlayers.size(), available);
                })
                .toList();
    }

    private List<PlayerAverageContrastMetric> averageContrastMetrics(PlayerStatistics target,
                                                                      List<PlayerStatistics> cohort) {
        return AVERAGE_CONTRAST_METRICS.stream()
                .map(definition -> {
                    BigDecimal value = safeValue(definition, target);
                    List<BigDecimal> values = cohort.stream()
                            .map(player -> safeValue(definition, player))
                            .toList();
                    BigDecimal minValue = values.stream()
                            .min(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    BigDecimal maxValue = values.stream()
                            .max(BigDecimal::compareTo)
                            .orElse(BigDecimal.ZERO);
                    return new PlayerAverageContrastMetric(
                            definition.key(), definition.label(), value, average(values), minValue, maxValue,
                            rank(value, definition.higherIsBetter(), player -> safeValue(definition, player), cohort),
                            cohort.size(), definition.higherIsBetter(), PERCENT_METRICS.contains(definition.key()));
                })
                .toList();
    }

    /**
     * 以同位置样本的 10%—90% 分位区间归一化，极端值不会压扁其他选手的能力差异。
     * 两端以外的值贴边；保留 10 分的可视下限，避免合格选手的图形坍缩到中心。
     */
    private BigDecimal robustRadarScore(BigDecimal value, boolean higherIsBetter, List<BigDecimal> values) {
        if (values.size() <= 1) {
            return RADAR_NEUTRAL_SCORE;
        }
        BigDecimal lower = quantile(values, new BigDecimal("0.10"));
        BigDecimal upper = quantile(values, new BigDecimal("0.90"));
        BigDecimal range = upper.subtract(lower);
        if (range.signum() <= 0) {
            return RADAR_NEUTRAL_SCORE;
        }
        BigDecimal normalized = higherIsBetter
                ? value.subtract(lower).divide(range, 8, RoundingMode.HALF_UP)
                : upper.subtract(value).divide(range, 8, RoundingMode.HALF_UP);
        normalized = normalized.max(BigDecimal.ZERO).min(BigDecimal.ONE);
        return RADAR_SCORE_FLOOR.add(HUNDRED.subtract(RADAR_SCORE_FLOOR).multiply(normalized))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal quantile(List<BigDecimal> values, BigDecimal percentile) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        BigDecimal index = BigDecimal.valueOf(sorted.size() - 1L).multiply(percentile);
        int lowerIndex = index.intValue();
        int upperIndex = Math.min(lowerIndex + 1, sorted.size() - 1);
        BigDecimal fraction = index.subtract(BigDecimal.valueOf(lowerIndex));
        return sorted.get(lowerIndex).add(sorted.get(upperIndex).subtract(sorted.get(lowerIndex))
                .multiply(fraction));
    }

    private BigDecimal safeValue(MetricDefinition definition, PlayerStatistics player) {
        BigDecimal value = definition.value().apply(player);
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 竞赛排名：1 + 严格优于当前选手的人数，并列同名次。 */
    private int rank(BigDecimal value, boolean higherIsBetter, Function<PlayerStatistics, BigDecimal> metric,
                     List<PlayerStatistics> cohort) {
        long strictlyBetter = cohort.stream()
                .map(metric)
                .filter(other -> higherIsBetter
                        ? other.compareTo(value) > 0
                        : other.compareTo(value) < 0)
                .count();
        return (int) (1 + strictlyBetter);
    }

    /** 同位置百分位得分（0～100），同值同分；单人样本直接给 100。 */
    private BigDecimal percentileScore(BigDecimal value, boolean higherIsBetter,
                                       Function<PlayerStatistics, BigDecimal> metric,
                                       List<PlayerStatistics> cohort) {
        if (cohort.size() <= 1) {
            return HUNDRED;
        }
        long weaker = cohort.stream()
                .map(metric)
                .filter(other -> higherIsBetter
                        ? other.compareTo(value) < 0
                        : other.compareTo(value) > 0)
                .count();
        return BigDecimal.valueOf(weaker)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(cohort.size() - 1L), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP);
    }

    private PlayerDetailStatisticsResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取选手详情 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, PlayerDetailStatisticsResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入选手详情 Redis 缓存失败，key={}", key, exception);
        }
    }
}
