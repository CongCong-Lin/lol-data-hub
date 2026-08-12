package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamCombinationStatisticsQueryTest {
    @Test
    void normalizesStagesTypeAndSortField() {
        var query = new TeamCombinationStatisticsQuery(
                List.of(new StageKey(239, 28), new StageKey(237, 102), new StageKey(237, 102)),
                TeamCombinationType.BOT_SUPPORT, 3, " WINNINGRATE ", SortDirection.ASC);

        assertThat(query.stages()).containsExactly(new StageKey(237, 102), new StageKey(239, 28));
        assertThat(query.sortBy()).isEqualTo("winningRate");
        assertThat(query.cacheFingerprint()).isEqualTo(
                "237:102,239:28:BOT_SUPPORT:3:winningRate:ASC");
    }

    @Test
    void rejectsInvalidThresholdAndSortField() {
        assertThatThrownBy(() -> new TeamCombinationStatisticsQuery(
                List.of(new StageKey(237, 102)), TeamCombinationType.MID_JUNGLE,
                -1, "pickCount", SortDirection.DESC)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamCombinationStatisticsQuery(
                List.of(new StageKey(237, 102)), TeamCombinationType.MID_JUNGLE,
                1, "unknown", SortDirection.DESC)).isInstanceOf(IllegalArgumentException.class);
    }
}
