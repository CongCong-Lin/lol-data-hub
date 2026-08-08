package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageKeyTest {
    @Test
    void rejectsExtraColonAndInnerWhitespace() {
        assertThatThrownBy(() -> StageKey.parse("1:2:"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StageKey.parse("1 : 2"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── parse ──────────────────────────────────────────────────

    @Test
    void parseValidCanonicalString() {
        StageKey key = StageKey.parse("237:102");
        assertThat(key.sourceSeasonId()).isEqualTo(237);
        assertThat(key.sourceStageId()).isEqualTo(102);
    }

    @Test
    void parseTrimsWhitespace() {
        StageKey key = StageKey.parse("  237:102  ");
        assertThat(key.sourceSeasonId()).isEqualTo(237);
        assertThat(key.sourceStageId()).isEqualTo(102);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "237", "237:102:extra", "abc:102", "237:xyz", "-1:102", "237:-1", "0:102", "237:0"})
    void parseRejectsInvalidFormats(String input) {
        assertThatThrownBy(() -> StageKey.parse(input))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parseRejectsNull() {
        assertThatThrownBy(() -> StageKey.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }

    // ── canonical ──────────────────────────────────────────────

    @Test
    void canonicalReturnsFormattedString() {
        StageKey key = new StageKey(237, 102);
        assertThat(key.canonical()).isEqualTo("237:102");
    }

    // ── 构造器正数校验 ──────────────────────────────────────────

    @Test
    void rejectsZeroSeasonId() {
        assertThatThrownBy(() -> new StageKey(0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("赛季 ID");
    }

    @Test
    void rejectsNegativeStageId() {
        assertThatThrownBy(() -> new StageKey(1, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("赛段 ID");
    }

    // ── compareTo ──────────────────────────────────────────────

    @Test
    void comparesBySeasonIdThenStageId() {
        StageKey a = new StageKey(1, 100);
        StageKey b = new StageKey(2, 1);
        StageKey c = new StageKey(1, 200);

        assertThat(a.compareTo(b)).isNegative();
        assertThat(b.compareTo(a)).isPositive();
        assertThat(a.compareTo(c)).isNegative();
        assertThat(a.compareTo(new StageKey(1, 100))).isZero();
    }

    // ── fromSeasonStages ───────────────────────────────────────

    @Test
    void fromSeasonStagesDeduplicatesAndSorts() {
        List<StageKey> keys = StageKey.fromSeasonStages(237, List.of(103L, 101L, 103L, 102L));
        assertThat(keys).containsExactly(
                new StageKey(237, 101),
                new StageKey(237, 102),
                new StageKey(237, 103)
        );
    }

    @Test
    void fromSeasonStagesHandlesEmptyInput() {
        assertThat(StageKey.fromSeasonStages(237, List.of())).isEmpty();
        assertThat(StageKey.fromSeasonStages(237, null)).isEmpty();
    }
}
