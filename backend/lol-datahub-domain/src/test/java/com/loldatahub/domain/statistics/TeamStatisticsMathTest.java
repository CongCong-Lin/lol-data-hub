package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TeamStatisticsMathTest {
    @Test
    void weightedAverageIsNotSimpleAverage() {
        // 1 场值 10 + 3 场值 20 => (10*1 + 20*3) / 4 = 17.5
        BigDecimal weightedSum = BigDecimal.valueOf(10).multiply(BigDecimal.valueOf(1))
                .add(BigDecimal.valueOf(20).multiply(BigDecimal.valueOf(3)));
        BigDecimal result = TeamStatisticsMath.weightedAverage(weightedSum, 4);
        assertThat(result).isEqualByComparingTo("17.500000");
    }

    @Test
    void ratioReturnsZeroWhenDenominatorIsZero() {
        assertThat(TeamStatisticsMath.ratio(0, 0)).isEqualByComparingTo("0");
    }

    @Test
    void ratioComputesCorrectly() {
        assertThat(TeamStatisticsMath.ratio(3, 4)).isEqualByComparingTo("0.750000");
    }

    @Test
    void weightedAverageReturnsZeroWhenTotalCountIsZero() {
        assertThat(TeamStatisticsMath.weightedAverage(BigDecimal.TEN, 0)).isEqualByComparingTo("0");
    }
}
