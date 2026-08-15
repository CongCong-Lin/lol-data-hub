package com.loldatahub.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loldatahub.domain.statistics.PlayerDetailNotFoundException;
import com.loldatahub.domain.statistics.PlayerDetailQuery;
import com.loldatahub.domain.statistics.PlayerAverageContrastMetric;
import com.loldatahub.domain.statistics.PlayerHeroUsage;
import com.loldatahub.domain.statistics.PlayerRadarMetric;
import com.loldatahub.domain.statistics.PlayerStatistics;
import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.RankedPlayerMetric;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.infrastructure.mapper.ChampionStatisticsMapper;
import com.loldatahub.infrastructure.mapper.PlayerHeroUsageMapper;
import com.loldatahub.infrastructure.mapper.SystemStateMapper;
import com.loldatahub.infrastructure.model.PlayerHeroUsageAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDetailStatisticsServiceTest {
    private static final List<StageKey> STAGES = List.of(new StageKey(237, 102));
    private static final LocalDateTime COLLECTED_AT = LocalDateTime.of(2026, 8, 1, 10, 0);

    private PlayerDetailStatisticsService service;
    private PlayerStatisticsService playerStatisticsService;
    private PlayerHeroUsageMapper heroUsageMapper;
    private ChampionStatisticsMapper championStatisticsMapper;
    private SystemStateMapper systemStateMapper;
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        playerStatisticsService = mock(PlayerStatisticsService.class);
        heroUsageMapper = mock(PlayerHeroUsageMapper.class);
        championStatisticsMapper = mock(ChampionStatisticsMapper.class);
        systemStateMapper = mock(SystemStateMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(systemStateMapper.currentDataVersion()).thenReturn(9L);
        when(championStatisticsMapper.findCollectedStageKeys(any())).thenReturn(STAGES);
        when(heroUsageMapper.findLatestCollectedAt(any())).thenReturn(COLLECTED_AT);
        when(heroUsageMapper.findQualifiedPlayerPositions(any(), anyLong(), anyInt()))
                .thenReturn(List.of("TOP"));
        service = new PlayerDetailStatisticsService(playerStatisticsService, heroUsageMapper,
                championStatisticsMapper, systemStateMapper, redisTemplate,
                new ObjectMapper().findAndRegisterModules(), Duration.ofHours(12));
    }

    private void mockCohort(List<PlayerStatistics> items) {
        when(playerStatisticsService.query(any())).thenReturn(
                new PlayerStatisticsResult(9L, 5, items.size(), items));
    }

    private static PlayerStatistics player(long sourcePlayerId, String name, BigDecimal kda,
                                           BigDecimal killPerGame, BigDecimal deathPerGame) {
        return player(sourcePlayerId, name, kda, killPerGame, deathPerGame,
                bd("0.6"), bd("0.25"), bd("0.22"));
    }

    private static PlayerStatistics player(long sourcePlayerId, String name, BigDecimal kda,
                                           BigDecimal killPerGame, BigDecimal deathPerGame,
                                           BigDecimal killParticipantPercent, BigDecimal damagePercent,
                                           BigDecimal goldPercent) {
        return new PlayerStatistics("key-" + sourcePlayerId, sourcePlayerId, name, null,
                List.of("TES"), List.of("TOP"), 10L, 20L, 1L, BigDecimal.ONE,
                40L, 30L, 20L, kda, killPerGame, bd("3"), deathPerGame,
                bd("12000"), bd("200"), bd("5"), bd("2"), killParticipantPercent, bd("100"),
                bd("800"), damagePercent, goldPercent,
                true);
    }

    private static PlayerStatistics playerWithoutDamage(long sourcePlayerId, String name, BigDecimal kda,
                                                        BigDecimal killPerGame, BigDecimal deathPerGame) {
        return new PlayerStatistics("key-" + sourcePlayerId, sourcePlayerId, name, null,
                List.of("TES"), List.of("TOP"), 10L, 20L, 1L, BigDecimal.ONE,
                40L, 30L, 20L, kda, killPerGame, bd("3"), deathPerGame,
                bd("12000"), bd("200"), bd("5"), bd("2"), bd("0.6"), bd("100"),
                null, bd("0.25"), bd("0.22"),
                true);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    @Test
    void reusesPlayerStatisticsCohortWithSamePositionAndThreshold() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        service.query(new PlayerDetailQuery(11L, List.of(new StageKey(237, 102)), "TOP", 5));

        ArgumentCaptor<PlayerStatisticsQuery> captor = ArgumentCaptor.forClass(PlayerStatisticsQuery.class);
        verify(playerStatisticsService).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(new StageKey(237, 102));
        assertThat(captor.getValue().position()).isEqualTo("TOP");
        assertThat(captor.getValue().minimumMatchCount()).isEqualTo(5);
    }

    @Test
    void ranksOnlyWithinCohortWithCompetitionRankingAndTies() {
        mockCohort(List.of(
                player(11L, "Bin", bd("10"), bd("3"), bd("1")),
                player(12L, "Zeus", bd("10"), bd("3"), bd("1")),
                player(13L, "Kiin", bd("9"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(13L, STAGES, "TOP", 5));

        assertThat(result.cohortSize()).isEqualTo(3);
        RankedPlayerMetric kda = metric(result, "kda");
        assertThat(kda.rank()).isEqualTo(3);
        assertThat(kda.cohortSize()).isEqualTo(3);
        assertThat(kda.higherIsBetter()).isTrue();
    }

    @Test
    void hidesDamageCoreMetricWhenDamageDataUnavailable() {
        mockCohort(List.of(playerWithoutDamage(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.coreMetrics()).extracting(RankedPlayerMetric::key)
                .doesNotContain("damagePerGame");
        assertThat(result.coreMetrics()).hasSize(15);
    }

    @Test
    void ranksDamageOnlyAmongPlayersWithDamageData() {
        mockCohort(List.of(
                player(11L, "Bin", bd("4"), bd("3"), bd("1")),
                playerWithoutDamage(12L, "Zeus", bd("6"), bd("5"), bd("2"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        RankedPlayerMetric damage = metric(result, "damagePerGame");
        assertThat(damage.rank()).isEqualTo(1);
        assertThat(damage.cohortSize()).isEqualTo(1);
    }

    @Test
    void buildsAverageContrastFromTheSameQualifiedPositionCohort() {
        mockCohort(List.of(
                player(11L, "Bin", bd("4"), bd("3"), bd("1")),
                player(12L, "Zeus", bd("6"), bd("5"), bd("2")),
                player(13L, "Kiin", bd("2"), bd("1"), bd("3"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        PlayerAverageContrastMetric kills = result.averageContrastMetrics().stream()
                .filter(metric -> metric.key().equals("killPerGame"))
                .findFirst()
                .orElseThrow();
        assertThat(kills.value()).isEqualByComparingTo("3");
        assertThat(kills.averageValue()).isEqualByComparingTo("3");
        assertThat(kills.minValue()).isEqualByComparingTo("1");
        assertThat(kills.maxValue()).isEqualByComparingTo("5");
        assertThat(kills.rank()).isEqualTo(2);
        assertThat(result.averageContrastMetrics()).hasSize(7);
        assertThat(result.averageContrastMetrics()).extracting(PlayerAverageContrastMetric::key)
                .containsExactly("killPerGame", "deathPerGame", "assistPerGame", "creepScorePerGame",
                        "damagePerGame", "damagePercent", "goldPerGame");
    }

    @Test
    void lowerDeathPerGameRanksFirst() {
        mockCohort(List.of(
                player(11L, "Bin", bd("4"), bd("3"), bd("3")),
                player(12L, "Zeus", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(12L, STAGES, "TOP", 5));

        RankedPlayerMetric deaths = metric(result, "deathPerGame");
        assertThat(deaths.higherIsBetter()).isFalse();
        assertThat(deaths.rank()).isEqualTo(1);
        assertThat(metric(result, "kda").rank()).isEqualTo(1);
    }

    @Test
    void robustScoresUseTheMiddleNinetyPercentRange() {
        mockCohort(List.of(
                player(11L, "Bin", bd("10"), bd("3"), bd("1")),
                player(12L, "Zeus", bd("8"), bd("3"), bd("1")),
                player(13L, "Kiin", bd("5"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        PlayerRadarMetric kda = radar(result, "kda");
        assertThat(kda.playerScore()).isEqualByComparingTo("100");
        assertThat(kda.averageScore()).isEqualByComparingTo("56.50");
        assertThat(kda.averageValue()).isEqualByComparingTo(bd("7.666667").toString());
        assertThat(kda.value()).isEqualByComparingTo("10");
        assertThat(kda.rank()).isEqualTo(1);
    }

    @Test
    void lowestValueKeepsMinimumVisibleRadarScore() {
        mockCohort(List.of(
                player(11L, "Bin", bd("10"), bd("3"), bd("1")),
                player(12L, "Zeus", bd("8"), bd("3"), bd("1")),
                player(13L, "Kiin", bd("5"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(13L, STAGES, "TOP", 5));

        assertThat(radar(result, "kda").playerScore()).isEqualByComparingTo("10");
    }

    @Test
    void singlePlayerCohortDoesNotDivideByZeroAndUsesNeutralScore() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.cohortSize()).isEqualTo(1);
        assertThat(radar(result, "kda").playerScore()).isEqualByComparingTo("55");
        assertThat(radar(result, "kda").averageScore()).isEqualByComparingTo("55");
        assertThat(metric(result, "kda").rank()).isEqualTo(1);
    }

    @Test
    void radarUsesTheSameEightMetricsForEveryPosition() {
        mockCohort(List.of(player(11L, "Xun", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult top = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));
        PlayerDetailStatisticsResult jug = service.query(
                new PlayerDetailQuery(11L, STAGES, "JUG", 5));
        PlayerDetailStatisticsResult sup = service.query(
                new PlayerDetailQuery(11L, STAGES, "SUP", 5));
        PlayerDetailStatisticsResult ad = service.query(
                new PlayerDetailQuery(11L, STAGES, "AD", 5));

        List<String> expectedKeys = List.of("kda", "killParticipantPercent", "creepScorePerGame",
                "goldGapPerGame", "killPerGame", "damagePercent", "damagePerGame", "deathPerGame");
        assertThat(top.radarMetrics()).extracting(PlayerRadarMetric::key).containsExactlyElementsOf(expectedKeys);
        assertThat(jug.radarMetrics()).extracting(PlayerRadarMetric::key).containsExactlyElementsOf(expectedKeys);
        assertThat(sup.radarMetrics()).extracting(PlayerRadarMetric::key).containsExactlyElementsOf(expectedKeys);
        assertThat(ad.radarMetrics()).extracting(PlayerRadarMetric::key).containsExactlyElementsOf(expectedKeys);
        assertThat(radar(top, "deathPerGame").rank()).isEqualTo(1);
        assertThat(radar(top, "damagePerGame").available()).isTrue();
    }

    @Test
    void findsTargetExactlyBySourcePlayerId() {
        mockCohort(List.of(
                player(11L, "Bin", bd("4"), bd("3"), bd("1")),
                player(12L, "Zeus", bd("6"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(12L, STAGES, "TOP", 5));

        assertThat(result.player().sourcePlayerId()).isEqualTo(12L);
        assertThat(result.player().playerName()).isEqualTo("Zeus");
        assertThat(result.player().teamNames()).containsExactly("TES");
        assertThat(result.player().matchCount()).isEqualTo(10L);
        assertThat(result.player().gameCount()).isEqualTo(20L);
    }

    @Test
    void exposesAllQualifiedPositionsForDetailSwitching() {
        mockCohort(List.of(player(11L, "Xun", bd("4"), bd("3"), bd("1"))));
        when(heroUsageMapper.findQualifiedPlayerPositions(STAGES, 11L, 5))
                .thenReturn(List.of("TOP", "JUG"));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.player().positions()).containsExactly("TOP", "JUG");
    }

    @Test
    void formatsRatioCoreMetricsAsPercentages() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(metric(result, "killParticipantPercent").formattedValue()).isEqualTo("60.00%");
        assertThat(metric(result, "damagePercent").formattedValue()).isEqualTo("25.00%");
        assertThat(metric(result, "goldPercent").formattedValue()).isEqualTo("22.00%");
    }

    @Test
    void formatsRatioCoreMetricsToTwoPercentageDecimals() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"),
                bd("0.619999"), bd("0.256789"), bd("0.218999"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(metric(result, "killParticipantPercent").formattedValue()).isEqualTo("62.00%");
        assertThat(metric(result, "damagePercent").formattedValue()).isEqualTo("25.68%");
        assertThat(metric(result, "goldPercent").formattedValue()).isEqualTo("21.90%");
    }

    @Test
    void unknownPlayerIdThrowsNotFound() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        when(heroUsageMapper.countPlayersBySourceId(99L)).thenReturn(0L);

        assertThatThrownBy(() -> service.query(new PlayerDetailQuery(99L, STAGES, "TOP", 5)))
                .isInstanceOf(PlayerDetailNotFoundException.class)
                .hasMessageContaining("选手 99 不存在");
    }

    @Test
    void playerWithoutRequestedPositionThrowsNotFound() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        when(heroUsageMapper.countPlayersBySourceId(12L)).thenReturn(1L);
        when(heroUsageMapper.countPlayerPositionRows(eq(STAGES), eq(12L), eq("TOP"))).thenReturn(0L);

        assertThatThrownBy(() -> service.query(new PlayerDetailQuery(12L, STAGES, "TOP", 5)))
                .isInstanceOf(PlayerDetailNotFoundException.class)
                .hasMessageContaining("没有 TOP 位置数据");
    }

    @Test
    void playerBelowSampleThresholdThrowsNotFound() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        when(heroUsageMapper.countPlayersBySourceId(12L)).thenReturn(1L);
        when(heroUsageMapper.countPlayerPositionRows(eq(STAGES), eq(12L), eq("TOP"))).thenReturn(2L);

        assertThatThrownBy(() -> service.query(new PlayerDetailQuery(12L, STAGES, "TOP", 5)))
                .isInstanceOf(PlayerDetailNotFoundException.class)
                .hasMessageContaining("未达到最低 5 场的样本要求");
    }

    @Test
    void heroUsageAggregatesAcrossStagesWithSameFactTableDenominator() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        List<StageKey> crossSeason = List.of(new StageKey(237, 102), new StageKey(239, 28));
        when(championStatisticsMapper.findCollectedStageKeys(crossSeason)).thenReturn(crossSeason);
        when(heroUsageMapper.aggregateHeroUsage(eq(crossSeason), eq(11L), eq("TOP"))).thenReturn(List.of(
                new PlayerHeroUsageAggregateRow(1L, "Annie", "安妮", "黑暗之女", null,
                        6L, 3L, 12L, 6L, 18L),
                new PlayerHeroUsageAggregateRow(2L, "Garen", "盖伦", "德玛西亚之力", null,
                        4L, 3L, 8L, 4L, 10L)));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, crossSeason, "TOP", 5));

        verify(heroUsageMapper).aggregateHeroUsage(eq(crossSeason), eq(11L), eq("TOP"));
        assertThat(result.heroUsageAvailable()).isTrue();
        assertThat(result.missingHeroStageKeys()).isEmpty();
        assertThat(result.heroUsageTotalGames()).isEqualTo(10L);
        assertThat(result.heroes()).extracting(PlayerHeroUsage::championChineseName)
                .containsExactly("安妮", "盖伦");

        PlayerHeroUsage annie = result.heroes().get(0);
        assertThat(annie.pickRate()).isEqualByComparingTo("0.6");
        assertThat(annie.winningRate()).isEqualByComparingTo("0.5");
        assertThat(annie.kda()).isEqualByComparingTo("5");
        assertThat(annie.killPerGame()).isEqualByComparingTo("2");
        assertThat(annie.deathPerGame()).isEqualByComparingTo("1");
        assertThat(annie.assistPerGame()).isEqualByComparingTo("3");
    }

    @Test
    void heroKdaUsesMaxDeathsOneAndEqualPicksSortByWinningRate() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        when(heroUsageMapper.aggregateHeroUsage(eq(STAGES), eq(11L), eq("TOP"))).thenReturn(List.of(
                new PlayerHeroUsageAggregateRow(1L, "A", "英雄一", null, null, 5L, 2L, 4L, 0L, 2L),
                new PlayerHeroUsageAggregateRow(2L, "B", "英雄二", null, null, 5L, 4L, 8L, 2L, 6L)));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.heroes()).extracting(PlayerHeroUsage::championChineseName)
                .containsExactly("英雄二", "英雄一");
        assertThat(result.heroes().get(1).kda()).isEqualByComparingTo("6");
    }

    @Test
    void jugQueryUsesJunPositionForHeroDetail() {
        mockCohort(List.of(player(11L, "Xun", bd("4"), bd("3"), bd("1"))));

        service.query(new PlayerDetailQuery(11L, STAGES, "JUG", 5));

        verify(heroUsageMapper).aggregateHeroUsage(eq(STAGES), eq(11L), eq("JUN"));
    }

    @Test
    void adQueryUsesBotPositionForHeroDetail() {
        mockCohort(List.of(player(11L, "Elk", bd("4"), bd("3"), bd("1"))));

        service.query(new PlayerDetailQuery(11L, STAGES, "AD", 5));

        verify(heroUsageMapper).aggregateHeroUsage(eq(STAGES), eq(11L), eq("BOT"));
    }

    @Test
    void missingHeroDetailStagesHideHeroUsageInsteadOfPartialStats() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));
        List<StageKey> crossSeason = List.of(new StageKey(237, 102), new StageKey(237, 104));
        when(championStatisticsMapper.findCollectedStageKeys(crossSeason))
                .thenReturn(List.of(new StageKey(237, 102)));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, crossSeason, "TOP", 5));

        assertThat(result.heroUsageAvailable()).isFalse();
        assertThat(result.missingHeroStageKeys()).containsExactly("237:104");
        assertThat(result.heroes()).isEmpty();
        assertThat(result.heroUsageTotalGames()).isZero();
        verify(heroUsageMapper, never()).aggregateHeroUsage(any(), anyLong(), anyString());
        assertThat(result.coreMetrics()).isNotEmpty();
    }

    @Test
    void cacheHitSkipsCohortQueryAndHeroAggregation() throws Exception {
        PlayerDetailStatisticsResult cached = new PlayerDetailStatisticsResult(
                9L, 5, "TOP", 1,
                new com.loldatahub.domain.statistics.PlayerDetailProfile(
                        11L, "Bin", null, List.of("TES"), List.of("TOP"), 10L, 20L),
                List.of(), List.of(), true, List.of(), 0L, List.of(), COLLECTED_AT);
        String cachedJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(cached);
        when(valueOperations.get(anyString())).thenReturn(cachedJson);

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.player().playerName()).isEqualTo("Bin");
        verify(playerStatisticsService, never()).query(any());
        verify(heroUsageMapper, never()).aggregateHeroUsage(any(), anyLong(), anyString());
    }

    @Test
    void writesCacheWithVersionedPlayerDetailKey() {
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        service.query(new PlayerDetailQuery(11L,
                List.of(new StageKey(237, 103), new StageKey(237, 102)), "TOP", 5));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(keyCaptor.capture(), anyString(), eq(Duration.ofHours(12)));
        assertThat(keyCaptor.getValue())
                .isEqualTo("loldatahub:stats:s10:v9:player-detail:11:237:102,237:103:TOP:5");
    }

    @Test
    void redisFailureFallsBackToDatabaseQuery() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        mockCohort(List.of(player(11L, "Bin", bd("4"), bd("3"), bd("1"))));

        PlayerDetailStatisticsResult result = service.query(
                new PlayerDetailQuery(11L, STAGES, "TOP", 5));

        assertThat(result.player().playerName()).isEqualTo("Bin");
        assertThat(result.latestCollectedAt()).isEqualTo(COLLECTED_AT);
    }

    private static RankedPlayerMetric metric(PlayerDetailStatisticsResult result, String key) {
        return result.coreMetrics().stream()
                .filter(metric -> metric.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static PlayerRadarMetric radar(PlayerDetailStatisticsResult result, String key) {
        return result.radarMetrics().stream()
                .filter(metric -> metric.key().equals(key))
                .findFirst()
                .orElseThrow();
    }
}
