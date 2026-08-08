package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StatisticsQueryTest {
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

        org.assertj.core.api.Assertions.assertThat(query.position()).isEqualTo("JUG");
        assertThatThrownBy(() -> new PlayerStatisticsQuery(
                1, List.of(1L), 0, "JUN", "kda", SortDirection.DESC
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("未知的选手位置：JUN");
    }
}
