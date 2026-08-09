package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SortDirectionTest {

    @Test
    void defaultsBlankDirectionToDescending() {
        assertThat(SortDirection.from(null)).isEqualTo(SortDirection.DESC);
        assertThat(SortDirection.from("  ")).isEqualTo(SortDirection.DESC);
    }

    @Test
    void acceptsKnownDirectionsCaseInsensitively() {
        assertThat(SortDirection.from(" ASC ")).isEqualTo(SortDirection.ASC);
        assertThat(SortDirection.from("Desc")).isEqualTo(SortDirection.DESC);
    }

    @Test
    void rejectsUnknownDirection() {
        assertThatThrownBy(() -> SortDirection.from("garbage"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的排序方向");
    }
}
