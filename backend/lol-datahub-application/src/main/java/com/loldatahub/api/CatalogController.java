package com.loldatahub.api;

import com.loldatahub.domain.catalog.Season;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogMapper catalogMapper;

    public CatalogController(CatalogMapper catalogMapper) {
        this.catalogMapper = catalogMapper;
    }

    @GetMapping("/seasons")
    ApiResponse<List<Season>> seasons() {
        return ApiResponse.success(catalogMapper.findSeasons());
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
            default -> throw new IllegalArgumentException("不支持的统计类型：" + statisticType);
        };
        return ApiResponse.success(rows.stream()
                .map(StageView::from)
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
            default -> throw new IllegalArgumentException("不支持的统计类型：" + statisticType);
        };
        return ApiResponse.success(rows.stream()
                .map(StageAvailabilityView::from)
                .toList());
    }
}
