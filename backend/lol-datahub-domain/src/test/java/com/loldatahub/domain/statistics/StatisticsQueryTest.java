package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new ChampionStatisticsQuery(1, List.of(1L), 0, "winRate", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new ChampionStatisticsQuery(1, List.of(3L, 1L, 3L, 2L), 0, "bpRate", SortDirection.DESC);
            assertThat(q.stageIds()).containsExactly(1L, 2L, 3L);
        }

        @Test
        void equivalentInputsProduceSameFingerprint() {
            var a = new ChampionStatisticsQuery(1, List.of(2L, 1L), 5, "PickCount", SortDirection.DESC);
            var b = new ChampionStatisticsQuery(1, List.of(1L, 2L), 5, "pickcount", SortDirection.DESC);
            assertThat(a.cacheFingerprint()).isEqualTo(b.cacheFingerprint());
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

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new TeamStatisticsQuery(1, List.of(1L), 0, "kills", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new TeamStatisticsQuery(1, List.of(5L, 2L, 5L), 0, "winningRate", SortDirection.DESC);
            assertThat(q.stageIds()).containsExactly(2L, 5L);
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

        @Test
        void rejectsUnknownSortField() {
            assertThatThrownBy(() -> new PlayerStatisticsQuery(1, List.of(1L), 0, null, "kills", SortDirection.DESC))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不支持的排序字段");
        }

        @Test
        void deduplicatesAndSortsStageIds() {
            var q = new PlayerStatisticsQuery(1, List.of(4L, 1L, 4L, 2L), 0, null, "kda", SortDirection.DESC);
            assertThat(q.stageIds()).containsExactly(1L, 2L, 4L);
        }

        @Test
        void equivalentInputsProduceSameFingerprint() {
            var a = new PlayerStatisticsQuery(1, List.of(2L, 1L), 3, null, "DamagePercent", SortDirection.DESC);
            var b = new PlayerStatisticsQuery(1, List.of(1L, 2L), 3, null, "damagepercent", SortDirection.DESC);
            assertThat(a.cacheFingerprint()).isEqualTo(b.cacheFingerprint());
        }
    }
}
