package com.loldatahub.api;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import com.loldatahub.infrastructure.model.CrossSeasonStageAvailabilityRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.function.ToLongFunction;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogMapper catalogMapper;
    private final PublicCatalogProperties publicCatalog;

    public CatalogController(CatalogMapper catalogMapper, PublicCatalogProperties publicCatalog) {
        this.catalogMapper = catalogMapper;
        this.publicCatalog = publicCatalog;
    }

    @GetMapping("/seasons")
    ApiResponse<List<Season>> seasons() {
        return ApiResponse.success(orderAndFilter(
                catalogMapper.findSeasons(), Season::sourceSeasonId, ignored -> true));
    }

    /**
     * 旧接口：按赛季查询赛段可用性（完全兼容）。
     */
    @GetMapping("/stages")
    ApiResponse<List<StageView>> stages(
            @RequestParam long seasonId,
            @RequestParam(defaultValue = "HERO") String statisticType
    ) {
        var rows = switch (statisticType.toUpperCase(java.util.Locale.ROOT)) {
            case "HERO" -> catalogMapper.findHeroStageAvailability(seasonId);
            case "TEAM" -> catalogMapper.findTeamStageAvailability(seasonId);
            case "PLAYER" -> catalogMapper.findPlayerStageAvailability(seasonId);
            case "COMBO" -> catalogMapper.findCombinationStageAvailability(seasonId);
            default -> throw new IllegalArgumentException("不支持的统计类型：" + statisticType);
        };
        return ApiResponse.success(rows.stream()
                .filter(row -> publicCatalog.containsStage(row.sourceSeasonId(), row.sourceStageId()))
                .map(row -> StageView.from(row, publicCatalog.displayStageName(
                        row.sourceSeasonId(), row.sourceStageId(), row.name())))
                .toList());
    }

    /**
     * 新接口：跨赛事赛段可用性查询。
     * GET /api/v1/catalog/stages/availability?statisticType=HERO&collectedOnly=true
     */
    @GetMapping("/stages/availability")
    ApiResponse<List<StageAvailabilityView>> stagesAvailability(
            @RequestParam(defaultValue = "HERO") String statisticType,
            @RequestParam(defaultValue = "false") boolean collectedOnly
    ) {
        var rows = switch (statisticType.toUpperCase(java.util.Locale.ROOT)) {
            case "HERO" -> catalogMapper.findAllHeroStageAvailability(collectedOnly);
            case "TEAM" -> catalogMapper.findAllTeamStageAvailability(collectedOnly);
            case "PLAYER" -> catalogMapper.findAllPlayerStageAvailability(collectedOnly);
            case "COMBO" -> catalogMapper.findAllCombinationStageAvailability(collectedOnly);
            default -> throw new IllegalArgumentException("不支持的统计类型：" + statisticType);
        };
        return ApiResponse.success(orderAndFilter(
                rows,
                CrossSeasonStageAvailabilityRow::sourceSeasonId,
                row -> publicCatalog.containsStage(row.sourceSeasonId(), row.sourceStageId())).stream()
                .map(row -> StageAvailabilityView.from(row, publicCatalog.displayStageName(
                        row.sourceSeasonId(), row.sourceStageId(), row.name())))
                .toList());
    }

    private <T> List<T> orderAndFilter(List<T> rows,
                                       ToLongFunction<T> seasonId,
                                       java.util.function.Predicate<T> visible) {
        return publicCatalog.visibleEvents().stream()
                .flatMap(event -> rows.stream()
                        .filter(visible)
                        .filter(row -> seasonId.applyAsLong(row) == event.seasonId()))
                .toList();
    }
}
