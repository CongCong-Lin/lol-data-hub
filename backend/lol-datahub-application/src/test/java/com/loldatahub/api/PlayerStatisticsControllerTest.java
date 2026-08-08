package com.loldatahub.api;

import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.PlayerStatisticsResult;
import com.loldatahub.statistics.PlayerStatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerStatisticsControllerTest {

    private final PlayerStatisticsService service = mock(PlayerStatisticsService.class);
    private final PlayerStatisticsController controller = new PlayerStatisticsController(service);

    @Test
    void newParamFormatSuccess() {
        var result = new PlayerStatisticsResult(1L, 5, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<PlayerStatisticsResult> response = controller.players(
                "237:102,239:28", null, null, 5, null, "kda", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<PlayerStatisticsQuery> captor = ArgumentCaptor.forClass(PlayerStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    @Test
    void oldParamFormatSuccess() {
        var result = new PlayerStatisticsResult(1L, 5, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<PlayerStatisticsResult> response = controller.players(
                null, 237L, List.of(102L, 103L), 5, null, "kda", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<PlayerStatisticsQuery> captor = ArgumentCaptor.forClass(PlayerStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(237, 103)
        );
    }

    @Test
    void bothFormatsReturn400() {
        assertThatThrownBy(() -> controller.players(
                "237:102", 237L, List.of(102L), 5, null, "kda", "desc"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能同时使用");
    }

    @Test
    void missingOldStageIdsReturn400() {
        assertThatThrownBy(() -> controller.players(
                null, 237L, List.of(), 5, null, "kda", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOldSeasonIdReturn400() {
        assertThatThrownBy(() -> controller.players(
                null, null, List.of(102L), 5, null, "kda", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noParamsReturn400() {
        assertThatThrownBy(() -> controller.players(
                null, null, null, 5, null, "kda", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void illegalStageKeyFormatReturn400() {
        assertThatThrownBy(() -> controller.players(
                "237:102:extra", null, null, 5, null, "kda", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
