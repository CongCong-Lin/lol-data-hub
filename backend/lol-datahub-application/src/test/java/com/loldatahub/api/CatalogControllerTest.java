package com.loldatahub.api;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.infrastructure.model.CrossSeasonStageAvailabilityRow;
import com.loldatahub.infrastructure.model.StageAvailabilityRow;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatalogControllerTest {
    private static final PublicCatalogProperties PUBLIC_CATALOG = new PublicCatalogProperties(List.of(
            new PublicCatalogProperties.VisibleEvent(237L, List.of(112L, 102L)),
            new PublicCatalogProperties.VisibleEvent(239L, List.of(28L))
    ), java.util.Map.of());

    @Test
    void exposesOnlyConfiguredSeasonsInConfiguredOrder() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findSeasons()).thenReturn(List.of(
                new Season(10101, "隐藏赛事", null, null, false),
                new Season(239, "2026季中冠军赛", null, null, false),
                new Season(237, "2026职业联赛", null, null, true)
        ));

        ApiResponse<List<Season>> response = controller.seasons();

        assertThat(response.data())
                .extracting(Season::sourceSeasonId)
                .containsExactly(237L, 239L);
    }

    @Test
    void exposesWhetherStageHasLocalStatistics() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
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
    void usesConfiguredPublicStageNameWithoutChangingSourceCatalog() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        PublicCatalogProperties catalog = new PublicCatalogProperties(
                List.of(new PublicCatalogProperties.VisibleEvent(237L, List.of(114L))),
                java.util.Map.of("237-114", "第三赛段淘汰赛"));
        CatalogController controller = new CatalogController(mapper, catalog);
        when(mapper.findHeroStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 114, "2026赛季季后赛", null, null, true, 9L, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "HERO");

        assertThat(response.data()).singleElement()
                .extracting(StageView::name)
                .isEqualTo("第三赛段淘汰赛");
    }

    @Test
    void hidesStagesOutsideConfiguredPublicCatalog() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findHeroStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "可见赛段", null, null, true, 80L, null),
                new StageAvailabilityRow(237, 999, "隐藏赛段", null, null, true, 1L, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "HERO");

        assertThat(response.data())
                .extracting(StageView::sourceStageId)
                .containsExactly(112L);
    }

    @Test
    void resolvesAvailabilityForRequestedStatisticType() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findTeamStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "第一赛段", null, null, true, null, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "team");

        assertThat(response.data()).singleElement().extracting(StageView::collected).isEqualTo(true);
    }

    @Test
    void resolvesPlayerAvailability() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findPlayerStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "第一赛段", null, null, false, null, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "PLAYER");

        assertThat(response.data()).singleElement().extracting(StageView::collected).isEqualTo(false);
    }

    @Test
    void resolvesCombinationAvailability() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findCombinationStageAvailability(237L)).thenReturn(List.of(
                new StageAvailabilityRow(237, 112, "第一赛段", null, null, true, 80L, null)
        ));

        ApiResponse<List<StageView>> response = controller.stages(237L, "COMBO");

        assertThat(response.data()).singleElement().extracting(StageView::collected).isEqualTo(true);
    }

    @Test
    void rejectsUnsupportedStatisticType() {
        CatalogController controller = new CatalogController(mock(CatalogMapper.class), PUBLIC_CATALOG);

        assertThatThrownBy(() -> controller.stages(237L, "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的统计类型：UNKNOWN");
    }

    // ── 跨赛事赛段可用性 ──────────────────────────────────────

    @Test
    void crossSeasonAvailabilityReturnsSeasonName() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findAllHeroStageAvailability(false)).thenReturn(List.of(
                new CrossSeasonStageAvailabilityRow(237, 102, "2025 LPL 春季赛", "第二赛段", null, null, true, 80L, null),
                new CrossSeasonStageAvailabilityRow(239, 28, "2025 MSI", "正赛", null, null, true, 50L, null)
        ));

        ApiResponse<List<StageAvailabilityView>> response = controller.stagesAvailability("HERO", false);

        assertThat(response.success()).isTrue();
        assertThat(response.data()).hasSize(2);
        assertThat(response.data().get(0).seasonName()).isEqualTo("2025 LPL 春季赛");
        assertThat(response.data().get(1).seasonName()).isEqualTo("2025 MSI");
    }

    @Test
    void crossSeasonAvailabilityUsesConfiguredPublicStageName() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        PublicCatalogProperties catalog = new PublicCatalogProperties(
                List.of(new PublicCatalogProperties.VisibleEvent(237L, List.of(114L))),
                java.util.Map.of("237-114", "第三赛段淘汰赛"));
        CatalogController controller = new CatalogController(mapper, catalog);
        when(mapper.findAllHeroStageAvailability(false)).thenReturn(List.of(
                new CrossSeasonStageAvailabilityRow(
                        237, 114, "2026职业联赛", "2026赛季季后赛",
                        null, null, true, 9L, null)
        ));

        ApiResponse<List<StageAvailabilityView>> response =
                controller.stagesAvailability("HERO", false);

        assertThat(response.data()).singleElement()
                .extracting(StageAvailabilityView::name)
                .isEqualTo("第三赛段淘汰赛");
    }

    @Test
    void crossSeasonAvailabilityWithCollectedOnly() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findAllHeroStageAvailability(true)).thenReturn(List.of(
                new CrossSeasonStageAvailabilityRow(237, 102, "2025 LPL 春季赛", "第二赛段", null, null, true, 80L, null)
        ));

        ApiResponse<List<StageAvailabilityView>> response = controller.stagesAvailability("HERO", true);

        assertThat(response.data()).singleElement()
                .extracting(StageAvailabilityView::seasonName)
                .isEqualTo("2025 LPL 春季赛");
    }

    @Test
    void crossSeasonAvailabilityHidesUnconfiguredEventsAndStages() {
        CatalogMapper mapper = mock(CatalogMapper.class);
        CatalogController controller = new CatalogController(mapper, PUBLIC_CATALOG);
        when(mapper.findAllTeamStageAvailability(false)).thenReturn(List.of(
                new CrossSeasonStageAvailabilityRow(10101, 1, "隐藏赛事", "隐藏赛段", null, null, true, null, null),
                new CrossSeasonStageAvailabilityRow(237, 999, "2026职业联赛", "隐藏赛段", null, null, true, null, null),
                new CrossSeasonStageAvailabilityRow(239, 28, "2026季中冠军赛", "入围赛", null, null, true, null, null),
                new CrossSeasonStageAvailabilityRow(237, 112, "2026职业联赛", "第一赛段", null, null, true, null, null)
        ));

        ApiResponse<List<StageAvailabilityView>> response = controller.stagesAvailability("TEAM", false);

        assertThat(response.data())
                .extracting(StageAvailabilityView::sourceSeasonId, StageAvailabilityView::sourceStageId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(237L, 112L),
                        org.assertj.core.groups.Tuple.tuple(239L, 28L)
                );
    }

    @Test
    void crossSeasonAvailabilityRejectsUnsupportedType() {
        CatalogController controller = new CatalogController(mock(CatalogMapper.class), PUBLIC_CATALOG);

        assertThatThrownBy(() -> controller.stagesAvailability("UNKNOWN", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("不支持的统计类型：UNKNOWN");
    }
}
