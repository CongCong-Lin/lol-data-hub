package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsQueryTest {

    // ── 原有校验 ──────────────────────────────────────────────

    @Test
    void rejectsNegativeChampionSampleThreshold() {
        assertThatThrownBy(() -> new ChampionStatisticsQuery(1, List.of(1L), -1, "bpRate", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最低出场次数不能小于 0");
    }

    @Test
    void rejectsNegativeTeamSampleThreshold() {
        assertThatThrownBy(() -> new TeamStatisticsQuery(1, List.of(1L), -1, "winningRate", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("最低比赛场数不能小于 0");
    }

    @Test
    void normalizesAndValidatesPlayerPosition() {
        var query = new PlayerStatisticsQuery(1, List.of(1L), 0, " jug ", "kda", SortDirection.DESC);

        assertThat(query.position()).isEqualTo("JUG");
        assertThatThrownBy(() -> new PlayerStatisticsQuery(
                1, List.of(1L), 0, "JUN", "kda", SortDirection.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未知的选手位置：JUN");
    }

    // ── 阈值上限校验 ───────────────────────────────────────────

    @Test
    void championRejectsThresholdOverMax() {
        assertThatThrownBy(() -> new ChampionStatisticsQuery(1, List.of(1L), 10001, "bpRate", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }

    @Test
    void teamRejectsThresholdOverMax() {
        assertThatThrownBy(() -> new TeamStatisticsQuery(1, List.of(1L), 10001, "winningRate", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }

    @Test
    void playerRejectsThresholdOverMax() {
        assertThatThrownBy(() -> new PlayerStatisticsQuery(1, List.of(1L), 10001, null, "kda", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过");
    }

    // ── 最多50个赛段 ──────────────────────────────────────────

    @Test
    void championRejectsMoreThan50Stages() {
        List<Long> manyStages = new ArrayList<>();
        for (long i = 1; i <= 51; i++) manyStages.add(i);
        assertThatThrownBy(() -> new ChampionStatisticsQuery(1, manyStages, 0, "bpRate", SortDirection.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多支持 50");
    }

    // ── 旧构造器兼容 ──────────────────────────────────────────

    @Test
    void oldConstructorProducesSameStagesAsCanonical() {
        var old = new ChampionStatisticsQuery(237, List.of(103L, 101L, 102L), 10, "bpRate", SortDirection.DESC);
        List<StageKey> expected = List.of(
                new StageKey(237, 101), new StageKey(237, 102), new StageKey(237, 103)
        );
        assertThat(old.stages()).containsExactlyElementsOf(expected);
    }

    // ── 跨 season 去重排序 ────────────────────────────────────

    @Test
    void crossSeasonDedupAndSort() {
        List<StageKey> stages = List.of(
                new StageKey(239, 28), new StageKey(237, 102),
                new StageKey(237, 102), new StageKey(237, 101)
        );
        var q = new ChampionStatisticsQuery(stages, 0, "bpRate", SortDirection.DESC);
        assertThat(q.stages()).containsExactly(
                new StageKey(237, 101), new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    // ── 跨赛事指纹稳定 ────────────────────────────────────────

    @Test
    void crossSeasonFingerprintStableRegardlessOfInputOrder() {
        List<StageKey> a = List.of(new StageKey(237, 102), new StageKey(239, 28));
        List<StageKey> b = List.of(new StageKey(239, 28), new StageKey(237, 102));
        var qa = new ChampionStatisticsQuery(a, 5, "bpRate", SortDirection.DESC);
        var qb = new ChampionStatisticsQuery(b, 5, "bpRate", SortDirection.DESC);
        assertThat(qa.cacheFingerprint()).isEqualTo(qb.cacheFingerprint());
    }

    @Test
    void crossSeasonTeamFingerprintStable() {
        List<StageKey> a = List.of(new StageKey(237, 102), new StageKey(239, 28));
        List<StageKey> b = List.of(new StageKey(239, 28), new StageKey(237, 102));
        var qa = new TeamStatisticsQuery(a, 2, "winningRate", SortDirection.ASC);
        var qb = new TeamStatisticsQuery(b, 2, "winningRate", SortDirection.ASC);
        assertThat(qa.cacheFingerprint()).isEqualTo(qb.cacheFingerprint());
    }

    @Test
    void crossSeasonPlayerFingerprintStable() {
        List<StageKey> a = List.of(new StageKey(237, 102), new StageKey(239, 28));
        List<StageKey> b = List.of(new StageKey(239, 28), new StageKey(237, 102));
        var qa = new PlayerStatisticsQuery(a, 3, null, "kda", SortDirection.DESC);
        var qb = new PlayerStatisticsQuery(b, 3, null, "kda", SortDirection.DESC);
        assertThat(qa.cacheFingerprint()).isEqualTo(qb.cacheFingerprint());
    }

    // ── ChampionStatisticsQuery ───────────────────────────────

    @Nested
    class ChampionQueryTest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        void defaultsSortByToBpRate(String input) {
            var q = new ChampionStatisticsQuery(1, List.of(1L), 0, input, null);
            assertThat(q.sortBy()).isEqualTo("bpRate");
            assertThat(q.sortDirection()).isEqualTo(SortDirection.DESC);
        }

        @ParameterizedTest
        @ValueSource(strings = {"PICKCOUNT", "PickCount", "pickcount", "pickCount", " pickCount "})
        void normalizesCaseInsensitive(String input) {
            var q = new ChampionStatisticsQuery(1, List.of(1L), 0, input, SortDirection.ASC);
            assertThat(q.sortBy()).isEqualTo("pickCount");
        }

        @ParameterizedTest
        @ValueSource(strings = {"championName", "positions", "pickCount", "pickRate", "banCount", "banRate",
                "bpRate", "winningCount", "winningRate", "totalKills", "killPerGame", "totalAssists",
                "assistPerGame", "totalDeaths", "deathPerGame", "kda", "mostUsedPlayers"})
        void acceptsEveryVisibleColumnAsSortField(String field) {
            assertThat(new ChampionStatisticsQuery(1, List.of(1L), 0, field, SortDirection.DESC).sortBy())
                    .isEqualTo(field);
        }

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new ChampionStatisticsQuery(1, List.of(1L), 0, "winRate", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new ChampionStatisticsQuery(1, List.of(3L, 1L, 3L, 2L), 0, "bpRate", SortDirection.DESC);
            assertThat(q.stages()).containsExactly(
                    new StageKey(1, 1), new StageKey(1, 2), new StageKey(1, 3));
        }

        @Test
        void equivalentInputsProduceSameFingerprint() {
            var a = new ChampionStatisticsQuery(1, List.of(2L, 1L), 5, "PickCount", SortDirection.DESC);
            var b = new ChampionStatisticsQuery(1, List.of(1L, 2L), 5, "pickcount", SortDirection.DESC);
            assertThat(a.cacheFingerprint()).isEqualTo(b.cacheFingerprint());
        }

        @Test
        void normalizesActualChampionPositionAndSeparatesCacheKeys() {
            var top = new ChampionStatisticsQuery(
                    List.of(new StageKey(239, 18)), 0, " top ", "winningRate", SortDirection.DESC);
            var mid = new ChampionStatisticsQuery(
                    List.of(new StageKey(239, 18)), 0, "MID", "winningRate", SortDirection.DESC);

            assertThat(top.position()).isEqualTo("TOP");
            assertThat(mid.position()).isEqualTo("MID");
            assertThat(top.cacheFingerprint()).isNotEqualTo(mid.cacheFingerprint());
        }

        @Test
        void rejectsUnknownChampionPosition() {
            assertThatThrownBy(() -> new ChampionStatisticsQuery(
                    List.of(new StageKey(239, 18)), 0, "AD", "winningRate", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未知的英雄分路");
        }
    }

    // ── TeamStatisticsQuery ───────────────────────────────────

    @Nested
    class TeamQueryTest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        void defaultsSortByToWinningRate(String input) {
            var q = new TeamStatisticsQuery(1, List.of(1L), 0, input, null);
            assertThat(q.sortBy()).isEqualTo("winningRate");
            assertThat(q.sortDirection()).isEqualTo(SortDirection.DESC);
        }

        @ParameterizedTest
        @ValueSource(strings = {"TOTALKILLS", "TotalKills", "totalkills", "totalKills", " totalKills "})
        void normalizesCaseInsensitive(String input) {
            var q = new TeamStatisticsQuery(1, List.of(1L), 0, input, SortDirection.ASC);
            assertThat(q.sortBy()).isEqualTo("totalKills");
        }

        @ParameterizedTest
        @ValueSource(strings = {"teamName", "matchCount", "gameCount", "matchWinCount", "winningRate",
                "totalKills", "killPerGame", "deathPerGame", "wardPlacedPerGame", "wardKilledPerGame",
                "goldPerGame", "baronKillPerGame", "drakeKillPerGame"})
        void acceptsEveryVisibleColumnAsSortField(String field) {
            assertThat(new TeamStatisticsQuery(1, List.of(1L), 0, field, SortDirection.DESC).sortBy())
                    .isEqualTo(field);
        }

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new TeamStatisticsQuery(1, List.of(1L), 0, "kills", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new TeamStatisticsQuery(1, List.of(5L, 2L, 5L), 0, "winningRate", SortDirection.DESC);
            assertThat(q.stages()).containsExactly(new StageKey(1, 2), new StageKey(1, 5));
        }

        @Test
        void equivalentInputsProduceSameFingerprint() {
            var a = new TeamStatisticsQuery(1, List.of(3L, 1L), 2, "MatchCount", SortDirection.ASC);
            var b = new TeamStatisticsQuery(1, List.of(1L, 3L), 2, "matchcount", SortDirection.ASC);
            assertThat(a.cacheFingerprint()).isEqualTo(b.cacheFingerprint());
        }
    }

    // ── PlayerStatisticsQuery ─────────────────────────────────

    @Nested
    class PlayerQueryTest {

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t"})
        void defaultsSortByToKda(String input) {
            var q = new PlayerStatisticsQuery(1, List.of(1L), 0, null, input, null);
            assertThat(q.sortBy()).isEqualTo("kda");
            assertThat(q.sortDirection()).isEqualTo(SortDirection.DESC);
        }

        @ParameterizedTest
        @ValueSource(strings = {"MVPCount", "MvPCount", "mvpcount", "mvpCount", " mvpCount "})
        void normalizesCaseInsensitive(String input) {
            var q = new PlayerStatisticsQuery(1, List.of(1L), 0, null, input, SortDirection.ASC);
            assertThat(q.sortBy()).isEqualTo("mvpCount");
        }

        @ParameterizedTest
        @ValueSource(strings = {"playerName", "positions", "matchCount", "gameCount", "mvpCount", "mvpVotes",
                "kda", "totalKills", "killPerGame", "totalAssists", "assistPerGame", "totalDeaths",
                "deathPerGame", "goldPerGame", "creepScorePerGame", "killParticipantPercent",
                "goldGapPerGame", "damagePerGame", "damagePercent", "goldPercent"})
        void acceptsEveryVisibleColumnAsSortField(String field) {
            assertThat(new PlayerStatisticsQuery(1, List.of(1L), 0, null, field, SortDirection.DESC).sortBy())
                    .isEqualTo(field);
        }

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new PlayerStatisticsQuery(1, List.of(1L), 0, null, "kills", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new PlayerStatisticsQuery(1, List.of(4L, 1L, 4L, 2L), 0, null, "kda", SortDirection.DESC);
            assertThat(q.stages()).containsExactly(
                    new StageKey(1, 1), new StageKey(1, 2), new StageKey(1, 4));
        }

        @Test
        void equivalentInputsProduceSameFingerprint() {
            var a = new PlayerStatisticsQuery(1, List.of(2L, 1L), 3, null, "DamagePercent", SortDirection.DESC);
            var b = new PlayerStatisticsQuery(1, List.of(1L, 2L), 3, null, "damagepercent", SortDirection.DESC);
            assertThat(a.cacheFingerprint()).isEqualTo(b.cacheFingerprint());
        }
    }
}
