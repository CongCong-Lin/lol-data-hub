package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamStatisticsQuery;
import com.loldatahub.statistics.TeamStatisticsResult;
import com.loldatahub.statistics.TeamStatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamStatisticsControllerTest {

    private final TeamStatisticsService service = mock(TeamStatisticsService.class);
    private final TeamStatisticsController controller = new TeamStatisticsController(service);

    @Test
    void newParamFormatSuccess() {
        var result = new TeamStatisticsResult(1L, 5, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<TeamStatisticsResult> response = controller.teams(
                "237:102,239:28", null, null, 5, "winningRate", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<TeamStatisticsQuery> captor = ArgumentCaptor.forClass(TeamStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    @Test
    void oldParamFormatSuccess() {
        var result = new TeamStatisticsResult(1L, 5, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<TeamStatisticsResult> response = controller.teams(
                null, 237L, List.of(102L, 103L), 5, "winningRate", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<TeamStatisticsQuery> captor = ArgumentCaptor.forClass(TeamStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(237, 103)
        );
    }

    @Test
    void bothFormatsReturn400() {
        assertThatThrownBy(() -> controller.teams(
                "237:102", 237L, List.of(102L), 5, "winningRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能同时使用");
    }

    @Test
    void missingOldStageIdsReturn400() {
        assertThatThrownBy(() -> controller.teams(
                null, 237L, List.of(), 5, "winningRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOldSeasonIdReturn400() {
        assertThatThrownBy(() -> controller.teams(
                null, null, List.of(102L), 5, "winningRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noParamsReturn400() {
        assertThatThrownBy(() -> controller.teams(
                null, null, null, 5, "winningRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void illegalStageKeyFormatReturn400() {
        assertThatThrownBy(() -> controller.teams(
                "abc:def", null, null, 5, "winningRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
