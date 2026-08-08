package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StageKeyParamParserTest {
    @Test
    void rejectsEmptyKeyBetweenCommas() {
        assertThatThrownBy(() -> StageKeyParamParser.parse("237:102,,239:28", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("空的赛段键");
    }

    @Test
    void parsesNewStageKeysParam() {
        List<StageKey> result = StageKeyParamParser.parse("237:102,239:28", null, null);
        assertThat(result).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    @Test
    void parsesNewStageKeysWithWhitespace() {
        List<StageKey> result = StageKeyParamParser.parse(" 237:102 , 239:28 ", null, null);
        assertThat(result).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    @Test
    void parsesOldParams() {
        List<StageKey> result = StageKeyParamParser.parse(null, 237L, List.of(102L, 103L));
        assertThat(result).containsExactly(
                new StageKey(237, 102), new StageKey(237, 103)
        );
    }

    @Test
    void rejectsBothParamsPresent() {
        assertThatThrownBy(() -> StageKeyParamParser.parse("237:102", 237L, List.of(102L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能同时使用");
    }

    @Test
    void rejectsOldSeasonIdWithoutStageIds() {
        assertThatThrownBy(() -> StageKeyParamParser.parse(null, 237L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stageIds 不能为空");
    }

    @Test
    void rejectsOldStageIdsWithoutSeasonId() {
        assertThatThrownBy(() -> StageKeyParamParser.parse(null, null, List.of(102L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须同时指定 seasonId");
    }

    @Test
    void rejectsNoParamsAtAll() {
        assertThatThrownBy(() -> StageKeyParamParser.parse(null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请指定查询参数");
    }

    @Test
    void rejectsBlankStageKeysParam() {
        assertThatThrownBy(() -> StageKeyParamParser.parse("  ", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidStageKeyFormat() {
        assertThatThrownBy(() -> StageKeyParamParser.parse("237,102", null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deduplicatesNewStageKeys() {
        List<StageKey> result = StageKeyParamParser.parse("237:102,237:102,239:28", null, null);
        assertThat(result).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }
}
