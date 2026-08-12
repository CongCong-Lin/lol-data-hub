package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerDetailQueryTest {
    @Test
    void deduplicatesAndSortsStageKeys() {
        PlayerDetailQuery query = new PlayerDetailQuery(
                2687L, List.of(new StageKey(239, 28), new StageKey(237, 102), new StageKey(239, 28)),
                "TOP", 5);

        assertThat(query.stages()).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28));
    }

    @Test
    void cacheFingerprintIsStableAcrossInputOrder() {
        PlayerDetailQuery first = new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 104), new StageKey(237, 102), new StageKey(237, 103)),
                "TOP", 5);
        PlayerDetailQuery second = new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102), new StageKey(237, 103), new StageKey(237, 104)),
                "top", 5);

        assertThat(first.cacheFingerprint()).isEqualTo(second.cacheFingerprint());
        assertThat(first.cacheFingerprint()).isEqualTo("2687:237:102,237:103,237:104:TOP:5");
    }

    @Test
    void cacheFingerprintDiffersWhenAnyParameterDiffers() {
        List<StageKey> stages = List.of(new StageKey(237, 102));
        PlayerDetailQuery base = new PlayerDetailQuery(2687L, stages, "TOP", 5);

        assertThat(new PlayerDetailQuery(2688L, stages, "TOP", 5).cacheFingerprint())
                .isNotEqualTo(base.cacheFingerprint());
        assertThat(new PlayerDetailQuery(2687L, stages, "JUG", 5).cacheFingerprint())
                .isNotEqualTo(base.cacheFingerprint());
        assertThat(new PlayerDetailQuery(2687L, stages, "TOP", 3).cacheFingerprint())
                .isNotEqualTo(base.cacheFingerprint());
    }

    @Test
    void rejectsNonPositivePlayerId() {
        assertThatThrownBy(() -> new PlayerDetailQuery(
                0L, List.of(new StageKey(237, 102)), "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选手 ID 必须为正整数");
        assertThatThrownBy(() -> new PlayerDetailQuery(
                -1L, List.of(new StageKey(237, 102)), "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选手 ID 必须为正整数");
    }

    @Test
    void rejectsEmptyStages() {
        assertThatThrownBy(() -> new PlayerDetailQuery(2687L, List.of(), "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要选择一个赛段");
        assertThatThrownBy(() -> new PlayerDetailQuery(2687L, null, "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少需要选择一个赛段");
    }

    @Test
    void rejectsMoreThanFiftyStages() {
        List<StageKey> stages = new ArrayList<>();
        for (int i = 1; i <= 51; i++) {
            stages.add(new StageKey(237, i));
        }

        assertThatThrownBy(() -> new PlayerDetailQuery(2687L, stages, "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最多支持 50 个赛段");
    }

    @Test
    void requiresPosition() {
        assertThatThrownBy(() -> new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102)), null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须指定位置");
        assertThatThrownBy(() -> new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102)), "  ", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须指定位置");
    }

    @Test
    void rejectsIllegalPosition() {
        assertThatThrownBy(() -> new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102)), "JUN", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知的选手位置");
    }

    @Test
    void rejectsThresholdOutsideRange() {
        assertThatThrownBy(() -> new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102)), "TOP", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能小于 0");
        assertThatThrownBy(() -> new PlayerDetailQuery(
                2687L, List.of(new StageKey(237, 102)), "TOP", 10001))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能超过 10000");
    }

    @Test
    void heroPositionMapsToPerGamePositionCodes() {
        List<StageKey> stages = List.of(new StageKey(237, 102));
        assertThat(new PlayerDetailQuery(1L, stages, "JUG", 0).heroPosition()).isEqualTo("JUN");
        assertThat(new PlayerDetailQuery(1L, stages, "AD", 0).heroPosition()).isEqualTo("BOT");
        assertThat(new PlayerDetailQuery(1L, stages, "TOP", 0).heroPosition()).isEqualTo("TOP");
        assertThat(new PlayerDetailQuery(1L, stages, "MID", 0).heroPosition()).isEqualTo("MID");
        assertThat(new PlayerDetailQuery(1L, stages, "SUP", 0).heroPosition()).isEqualTo("SUP");
    }
}
