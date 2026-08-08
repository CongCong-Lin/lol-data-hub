package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatisticsMathTest {
    @Test
    void recomputesCrossStageRateFromAddedCountersInsteadOfAveragingPercentages() {
        long stageAPicks = 1;
        long stageAWins = 1;
        long stageBPicks = 19;
        long stageBWins = 9;

        var crossStageRate = StatisticsMath.ratio(stageAWins + stageBWins, stageAPicks + stageBPicks);

        assertThat(crossStageRate).isEqualByComparingTo("0.500000");
        assertThat(crossStageRate).isNotEqualByComparingTo("0.736842");
    }

    @Test
    void returnsZeroWhenThereIsNoSample() {
        assertThat(StatisticsMath.ratio(0, 0)).isEqualByComparingTo("0");
    }
}
