package com.loldatahub.domain.statistics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerIdentityTest {
    @Test
    void usesIdWhenPlayerIdIsPositive() {
        assertThat(PlayerIdentity.resolve(12345L, "JackeyLove")).isEqualTo("id:12345");
    }

    @Test
    void usesNameWhenPlayerIdIsNull() {
        assertThat(PlayerIdentity.resolve(null, "Rookie")).isEqualTo("name:rookie");
    }

    @Test
    void usesNameWhenPlayerIdIsZero() {
        assertThat(PlayerIdentity.resolve(0L, "Rookie")).isEqualTo("name:rookie");
    }

    @Test
    void trimsAndLowercasesName() {
        assertThat(PlayerIdentity.resolve(null, "  JackeyLove  ")).isEqualTo("name:jackeylove");
    }

    @Test
    void throwsWhenPlayerIdInvalidAndNameBlank() {
        assertThatThrownBy(() -> PlayerIdentity.resolve(null, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选手名称不能为空");
    }

    @Test
    void throwsWhenPlayerIdInvalidAndNameNull() {
        assertThatThrownBy(() -> PlayerIdentity.resolve(null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选手名称不能为空");
    }
}
