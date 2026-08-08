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
}
