package com.loldatahub.api;

import com.loldatahub.domain.statistics.ChampionStatisticsQuery;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.ChampionStatisticsResult;
import com.loldatahub.statistics.ChampionStatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChampionStatisticsControllerTest {

    private final ChampionStatisticsService service = mock(ChampionStatisticsService.class);
    private final ChampionStatisticsController controller = new ChampionStatisticsController(service);

    @Test
    void newParamFormatSuccess() {
        var result = new ChampionStatisticsResult(1L, 10, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<ChampionStatisticsResult> response = controller.champions(
                "237:102,239:28", null, null, 10, null, "bpRate", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<ChampionStatisticsQuery> captor = ArgumentCaptor.forClass(ChampionStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28)
        );
    }

    @Test
    void oldParamFormatSuccess() {
        var result = new ChampionStatisticsResult(1L, 10, 0, List.of());
        when(service.query(any())).thenReturn(result);

        ApiResponse<ChampionStatisticsResult> response = controller.champions(
                null, 237L, List.of(102L, 103L), 10, null, "bpRate", "desc"
        );

        assertThat(response.success()).isTrue();
        ArgumentCaptor<ChampionStatisticsQuery> captor = ArgumentCaptor.forClass(ChampionStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(237, 103)
        );
    }

    @Test
    void bothFormatsReturn400() {
        assertThatThrownBy(() -> controller.champions(
                "237:102", 237L, List.of(102L), 10, null, "bpRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能同时使用");
    }

    @Test
    void missingOldStageIdsReturn400() {
        assertThatThrownBy(() -> controller.champions(
                null, 237L, List.of(), 10, null, "bpRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingOldSeasonIdReturn400() {
        assertThatThrownBy(() -> controller.champions(
                null, null, List.of(102L), 10, null, "bpRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noParamsReturn400() {
        assertThatThrownBy(() -> controller.champions(
                null, null, null, 10, null, "bpRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void illegalStageKeyFormatReturn400() {
        assertThatThrownBy(() -> controller.champions(
                "237-102", null, null, 10, null, "bpRate", "desc"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void positionIsPassedAsNormalizedServerSideFilter() {
        when(service.query(any())).thenReturn(new ChampionStatisticsResult(1L, 0, 0, List.of()));

        controller.champions("239:18,239:28", null, null, 0, " mid ", "winningRate", "desc");

        ArgumentCaptor<ChampionStatisticsQuery> captor = ArgumentCaptor.forClass(ChampionStatisticsQuery.class);
        verify(service).query(captor.capture());
        assertThat(captor.getValue().position()).isEqualTo("MID");
    }
}
