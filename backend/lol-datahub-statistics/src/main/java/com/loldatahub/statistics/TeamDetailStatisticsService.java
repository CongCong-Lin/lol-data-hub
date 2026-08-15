package com.loldatahub.statistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.RankedTeamMetric;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.StatisticsMath;
import com.loldatahub.domain.statistics.TeamDetailNotFoundException;
import com.loldatahub.domain.statistics.TeamDetailProfile;
import com.loldatahub.domain.statistics.TeamDetailQuery;
import com.loldatahub.domain.statistics.TeamDetailStatisticsResult;
import com.loldatahub.domain.statistics.TeamLineupPreference;
import com.loldatahub.domain.statistics.TeamPlayerUsage;
import com.loldatahub.domain.statistics.TeamStatistics;
import com.loldatahub.domain.statistics.TeamStatisticsQuery;
import com.loldatahub.infrastructure.mapper.MatchGameMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.mapper.TeamStatisticsMapper;
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
 * 战队详情页统计服务。
 * 核心指标与排名复用战队统计列表的聚合与缓存，阵容偏好与选手名单来自独立查询。
 * 排名、格式化全部由后端完成，口径与战队统计列表保持一致。
 */
@Service
public class TeamDetailStatisticsService {
    private static final Logger log = LoggerFactory.getLogger(TeamDetailStatisticsService.class);
    private static final TypeReference<TeamDetailStatisticsResult> CACHE_TYPE = new TypeReference<>() { };
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RECENT_GAME_LIMIT = 10;
    private static final Set<String> PERCENT_METRICS = Set.of(
            "winningRate", "drakeControlRate", "baronControlRate", "firstBloodRate");

    private record MetricDefinition(String key, String label, boolean higherIsBetter,
                                    Function<TeamStatistics, BigDecimal> value, boolean integerDisplay) {
    }

    /** 战队核心指标口径与战队统计列表完全一致（复用 TeamStatisticsService 的聚合结果）。 */
    private static final List<MetricDefinition> CORE_METRICS = List.of(
            new MetricDefinition("matchCount", "系列赛数", true, t -> BigDecimal.valueOf(t.matchCount()), true),
            new MetricDefinition("gameCount", "小局数", true, t -> BigDecimal.valueOf(t.gameCount()), true),
            new MetricDefinition("matchWinCount", "系列赛胜场", true, t -> BigDecimal.valueOf(t.matchWinCount()), true),
            new MetricDefinition("winningRate", "胜率", true, TeamStatistics::winningRate, false),
            new MetricDefinition("kda", "KDA", true, TeamStatistics::kda, false),
            new MetricDefinition("killPerGame", "场均击杀", true, TeamStatistics::killPerGame, false),
            new MetricDefinition("deathPerGame", "场均死亡", false, TeamStatistics::deathPerGame, false),
            new MetricDefinition("damagePerGame", "场均伤害", true, TeamStatistics::damagePerGame, false),
            new MetricDefinition("goldPerGame", "场均经济", true, TeamStatistics::goldPerGame, false),
            new MetricDefinition("goldPerMinute", "分均经济", true, TeamStatistics::goldPerMinute, false),
            new MetricDefinition("wardPlacedPerMinute", "分均插眼", true, TeamStatistics::wardPlacedPerMinute, false),
            new MetricDefinition("wardKilledPerMinute", "分均排眼", true, TeamStatistics::wardKilledPerMinute, false),
            new MetricDefinition("creepScorePerMinute", "分均补刀", true, TeamStatistics::creepScorePerMinute, false),
            new MetricDefinition("drakeControlRate", "小龙控制率", true, TeamStatistics::drakeControlRate, false),
            new MetricDefinition("baronControlRate", "大龙控制率", true, TeamStatistics::baronControlRate, false),
            new MetricDefinition("firstBloodRate", "一血率", true, TeamStatistics::firstBloodRate, false),
            new MetricDefinition("turretKillPerGame", "场均推塔", true, TeamStatistics::turretKillPerGame, false),
            new MetricDefinition("turretLostPerGame", "场均被推塔", false, TeamStatistics::turretLostPerGame, false),
            new MetricDefinition("averageGameDurationSeconds", "场均时长(秒)", true, TeamStatistics::averageGameDurationSeconds, true)
    );

    private final TeamStatisticsService teamStatisticsService;
    private final TeamStatisticsMapper mapper;
    private final MatchGameMapper matchGameMapper;
    private final SystemStateMapper systemStateMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;

    public TeamDetailStatisticsService(TeamStatisticsService teamStatisticsService,
                                       TeamStatisticsMapper mapper,
                                       MatchGameMapper matchGameMapper,
                                       SystemStateMapper systemStateMapper,
                                       StringRedisTemplate redisTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${lol-datahub.cache.statistics-ttl:PT12H}") Duration cacheTtl) {
        this.teamStatisticsService = teamStatisticsService;
        this.mapper = mapper;
        this.matchGameMapper = matchGameMapper;
        this.systemStateMapper = systemStateMapper;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = cacheTtl;
    }

    public TeamDetailStatisticsResult query(TeamDetailQuery query) {
        long dataVersion = systemStateMapper.currentDataVersion();
        String cacheKey = "loldatahub:stats:s7:v" + dataVersion + ":team-detail:" + query.cacheFingerprint();
        TeamDetailStatisticsResult cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 复用战队统计列表聚合：与列表页相同的公式、门槛与加权口径
        List<TeamStatistics> cohort = teamStatisticsService.query(new TeamStatisticsQuery(
                query.stages(), query.minimumMatchCount(), "teamName", SortDirection.ASC)).items();

        TeamStatistics target = cohort.stream()
                .filter(team -> team.teamId() == query.sourceTeamId())
                .findFirst()
                .orElseThrow(() -> new TeamDetailNotFoundException(
                        "所选赛段内不存在战队 " + query.sourceTeamId() + " 的数据"
                                + "（或未达到最低 " + query.minimumMatchCount() + " 场的样本要求）"));

        List<TeamLineupPreference> lineupPreferences = lineupPreferences(query, target);
        List<TeamPlayerUsage> players = mapper.aggregateTeamPlayers(query.stages(), query.sourceTeamId())
                .stream().map(row -> new TeamPlayerUsage(
                        row.sourcePlayerId(), row.playerName(), row.playerAvatar(), row.position(),
                        row.matchCount(), row.gameCount()))
                .toList();
        List<com.loldatahub.domain.statistics.MatchGameRecord> recentGames =
                matchGameMapper.findRecentGames(query.stages(), query.sourceTeamId(), RECENT_GAME_LIMIT)
                        .stream().map(MatchGameStatisticsService::toGameRecord).toList();

        TeamDetailStatisticsResult result = new TeamDetailStatisticsResult(
                dataVersion,
                query.minimumMatchCount(),
                cohort.size(),
                new TeamDetailProfile(target.teamId(), target.teamName(), target.teamLogo(),
                        target.matchCount(), target.gameCount(), target.matchWinCount()),
                coreMetrics(target, cohort),
                lineupPreferences,
                players,
                recentGames,
                matchGameMapper.findLatestCollectedAt(query.stages()));
        writeCache(cacheKey, result);
        return result;
    }

    private List<TeamLineupPreference> lineupPreferences(TeamDetailQuery query, TeamStatistics target) {
        List<TeamLineupPreference> preferences = mapper.aggregateLineupPreferences(
                        query.stages(), query.sourceTeamId())
                .stream().map(row -> new TeamLineupPreference(
                        row.position(), row.sourceChampionId(), row.championName(),
                        row.championChineseName(), row.championLogo(), row.pickCount(),
                        StatisticsMath.ratio(row.pickCount(), target.gameCount()),
                        row.winningCount(), StatisticsMath.ratio(row.winningCount(), row.pickCount())))
                .toList();
        return preferences.stream()
                .sorted(Comparator.comparing(TeamLineupPreference::position)
                        .thenComparing(Comparator.comparingLong(TeamLineupPreference::pickCount).reversed()))
                .toList();
    }

    private List<RankedTeamMetric> coreMetrics(TeamStatistics target, List<TeamStatistics> cohort) {
        return CORE_METRICS.stream()
                .filter(definition -> definition.value().apply(target) != null)
                .map(definition -> {
                    List<TeamStatistics> comparableTeams = cohort.stream()
                            .filter(team -> definition.value().apply(team) != null)
                            .toList();
                    BigDecimal value = definition.value().apply(target);
                    int rank = rank(value, definition.higherIsBetter(), definition.value(), comparableTeams);
                    String formatted = formatMetricValue(definition, value);
                    return new RankedTeamMetric(definition.key(), definition.label(), value,
                            formatted, rank, comparableTeams.size(), definition.higherIsBetter());
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

    /** 竞赛排名：1 + 严格优于当前战队的人数，并列同名次。 */
    private int rank(BigDecimal value, boolean higherIsBetter, Function<TeamStatistics, BigDecimal> metric,
                     List<TeamStatistics> cohort) {
        long strictlyBetter = cohort.stream()
                .map(metric)
                .filter(other -> higherIsBetter
                        ? other.compareTo(value) > 0
                        : other.compareTo(value) < 0)
                .count();
        return (int) (1 + strictlyBetter);
    }

    private TeamDetailStatisticsResult readCache(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null ? null : objectMapper.readValue(json, CACHE_TYPE);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("读取战队详情 Redis 缓存失败，key={}", key, exception);
            return null;
        }
    }

    private void writeCache(String key, TeamDetailStatisticsResult result) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(result), cacheTtl);
        } catch (DataAccessException | JsonProcessingException exception) {
            log.warn("写入战队详情 Redis 缓存失败，key={}", key, exception);
        }
    }
}
