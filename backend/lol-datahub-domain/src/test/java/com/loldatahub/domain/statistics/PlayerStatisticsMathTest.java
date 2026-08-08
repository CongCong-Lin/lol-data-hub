package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerStatisticsMathTest {
    @Test
    void ratioReturnsZeroWhenDenominatorIsZero() {
        assertThat(PlayerStatisticsMath.ratio(0, 0)).isEqualByComparingTo("0");
    }

    @Test
    void ratioComputesCorrectly() {
        assertThat(PlayerStatisticsMath.ratio(10, 4)).isEqualByComparingTo("2.500000");
    }

    @Test
    void perGameReturnsZeroWhenMatchCountIsZero() {
        assertThat(PlayerStatisticsMath.perGame(100, 0)).isEqualByComparingTo("0");
    }

    @Test
    void perGameComputesCorrectly() {
        assertThat(PlayerStatisticsMath.perGame(150, 20)).isEqualByComparingTo("7.500000");
    }

    @Test
    void splitCsvHandlesNull() {
        assertThat(PlayerStatisticsMath.splitCsv(null)).isEmpty();
    }

    @Test
    void splitCsvHandlesBlank() {
        assertThat(PlayerStatisticsMath.splitCsv("  ")).isEmpty();
    }

    @Test
    void splitCsvDeduplicatesAndTrims() {
        assertThat(PlayerStatisticsMath.splitCsv("AD, MID ,AD, TOP")).containsExactly("AD", "MID", "TOP");
    }

    @Test
    void validatePositionAcceptsKnownPositions() {
        assertThat(PlayerStatisticsMath.validatePosition("top")).isEqualTo("TOP");
        assertThat(PlayerStatisticsMath.validatePosition("jug")).isEqualTo("JUG");
        assertThat(PlayerStatisticsMath.validatePosition("MID")).isEqualTo("MID");
        assertThat(PlayerStatisticsMath.validatePosition("AD")).isEqualTo("AD");
    }

    @Test
    void validatePositionReturnsNullForBlank() {
        assertThat(PlayerStatisticsMath.validatePosition("  ")).isNull();
    }

    @Test
    void validatePositionThrowsForUnknown() {
        assertThatThrownBy(() -> PlayerStatisticsMath.validatePosition("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知的选手位置");
    }
}
