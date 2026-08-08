package com.loldatahub.api;

import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.infrastructure.model.StageAvailabilityRow;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogControllerTest {
    @Test
    void exposesWhetherStageHasLocalStatistics() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper);
        OffsetDateTime collectedAt = OffsetDateTime.parse("2026-08-08T08:00:00Z");
        when(mapper.findHeroStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "第一赛段", null, null, true, 80L, collectedAt),
                new StageAvailabilityRow(237, 102, "第二赛段", null, null, false, null, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "HERO");

        assertThat(response.success()).isTrue();
        assertThat(response.data()).containsExactly(
                new StageView(237, 112, "第一赛段", null, null, true, 80L, collectedAt),
                new StageView(237, 102, "第二赛段", null, null, false, null, null)
        );
    }

    @Test
    void resolvesAvailabilityForRequestedStatisticType() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper);
        when(mapper.findTeamStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "第一赛段", null, null, true, null, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "team");

        assertThat(response.data()).singleElement().extracting(StageView::collected).isEqualTo(true);
    }

    @Test
    void rejectsUnsupportedStatisticType() {
        CatalogController controller = new CatalogController(mock(CatalogMapper.class));

        assertThatThrownBy(() -> controller.stages(237L, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的统计类型：UNKNOWN");
    }
}
