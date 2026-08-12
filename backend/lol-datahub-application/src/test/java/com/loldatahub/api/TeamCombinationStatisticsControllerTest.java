package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamCombinationStatisticsQuery;
import com.loldatahub.domain.statistics.TeamCombinationType;
import com.loldatahub.statistics.TeamCombinationStatisticsResult;
import com.loldatahub.statistics.TeamCombinationStatisticsService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamCombinationStatisticsControllerTest {
    private final TeamCombinationStatisticsService service = mock(TeamCombinationStatisticsService.class);
    private final TeamCombinationStatisticsController controller = new TeamCombinationStatisticsController(service);

    @Test
    void parsesCrossSeasonCombinationQuery() {
        when(service.query(any())).thenReturn(new TeamCombinationStatisticsResult(
                1, TeamCombinationType.BOT_SUPPORT, 3, 0, List.of()));

        var response = controller.combinations(
                "237:102,239:28", null, null, "bot_support", 3, "winningRate", "desc");

        assertThat(response.success()).isTrue();
        ArgumentCaptor<TeamCombinationStatisticsQuery> query =
                ArgumentCaptor.forClass(TeamCombinationStatisticsQuery.class);
        verify(service).query(query.capture());
        assertThat(query.getValue().stages()).containsExactly(
                new StageKey(237, 102), new StageKey(239, 28));
        assertThat(query.getValue().combinationType()).isEqualTo(TeamCombinationType.BOT_SUPPORT);
    }

    @Test
    void rejectsUnsupportedCombinationType() {
        assertThatThrownBy(() -> controller.combinations(
                "237:102", null, null, "TOP_MID", 3, "pickCount", "desc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持的组合类型");
    }
}
