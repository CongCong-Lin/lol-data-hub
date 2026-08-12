package com.loldatahub.api;

import com.loldatahub.domain.statistics.PlayerDetailNotFoundException;
import com.loldatahub.domain.statistics.PlayerDetailQuery;
import com.loldatahub.statistics.PlayerDetailStatisticsResult;
import com.loldatahub.statistics.PlayerDetailStatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDetailStatisticsControllerTest {
    private final PlayerDetailStatisticsService service = mock(PlayerDetailStatisticsService.class);
    private final PlayerDetailStatisticsController controller = new PlayerDetailStatisticsController(service);

    private static PlayerDetailStatisticsResult emptyResult() {
        return new PlayerDetailStatisticsResult(9L, 5, "TOP", 0, null,
                List.of(), List.of(), true, List.of(), 0L, List.of(), null);
    }

    @Test
    void parsesStageKeysQueryIntoPlayerDetailQuery() {
        when(service.query(any())).thenReturn(emptyResult());

        ApiResponse<PlayerDetailStatisticsResult> response = controller.playerDetail(
                2687L, "237:103,237:102", null, null, "TOP", 5);

        assertThat(response.success()).isTrue();
        ArgumentCaptor<PlayerDetailQuery> captor = ArgumentCaptor.forClass(PlayerDetailQuery.class);
        verify(service).query(captor.capture());
        PlayerDetailQuery query = captor.getValue();
        assertThat(query.sourcePlayerId()).isEqualTo(2687L);
        assertThat(query.stages()).extracting(
                stage -> stage.canonical()).containsExactly("237:102", "237:103");
        assertThat(query.position()).isEqualTo("TOP");
        assertThat(query.minimumMatchCount()).isEqualTo(5);
    }

    @Test
    void supportsCrossSeasonCompositeKeys() {
        when(service.query(any())).thenReturn(emptyResult());

        controller.playerDetail(2687L, "237:102,239:28", null, null, "JUG", 3);

        ArgumentCaptor<PlayerDetailQuery> captor = ArgumentCaptor.forClass(PlayerDetailQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).extracting(
                stage -> stage.canonical()).containsExactly("237:102", "239:28");
        assertThat(captor.getValue().heroPosition()).isEqualTo("JUN");
    }

    @Test
    void supportsLegacySeasonAndStageIdsParameters() {
        when(service.query(any())).thenReturn(emptyResult());

        controller.playerDetail(2687L, null, 237L, List.of(102L, 103L), "TOP", 5);

        ArgumentCaptor<PlayerDetailQuery> captor = ArgumentCaptor.forClass(PlayerDetailQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).extracting(
                stage -> stage.canonical()).containsExactly("237:102", "237:103");
    }

    @Test
    void rejectsMissingStageParameters() {
        assertThatThrownBy(() -> controller.playerDetail(2687L, null, null, null, "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidPlayerId() {
        assertThatThrownBy(() -> controller.playerDetail(0L, "237:102", null, null, "TOP", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("选手 ID 必须为正整数");
    }

    @Test
    void rejectsMissingPosition() {
        assertThatThrownBy(() -> controller.playerDetail(2687L, "237:102", null, null, null, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须指定位置");
    }

    @Test
    void rejectsIllegalPosition() {
        assertThatThrownBy(() -> controller.playerDetail(2687L, "237:102", null, null, "JUN", 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知的选手位置");
    }

    @Test
    void rejectsThresholdOutsideRange() {
        assertThatThrownBy(() -> controller.playerDetail(2687L, "237:102", null, null, "TOP", -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> controller.playerDetail(2687L, "237:102", null, null, "TOP", 10001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propagatesNotFoundForUnknownPlayer() {
        when(service.query(any())).thenThrow(new PlayerDetailNotFoundException("选手 99 不存在"));

        assertThatThrownBy(() -> controller.playerDetail(99L, "237:102", null, null, "TOP", 5))
                .isInstanceOf(PlayerDetailNotFoundException.class)
                .hasMessageContaining("选手 99 不存在");
    }
}
