package com.loldatahub.api;

import com.loldatahub.domain.statistics.ChampionCounterQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.ChampionCounterResult;
import com.loldatahub.statistics.ChampionCounterStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics/champions")
public class ChampionCounterStatisticsController {
    private final ChampionCounterStatisticsService service;

    public ChampionCounterStatisticsController(ChampionCounterStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/{championId}/counters")
    ApiResponse<ChampionCounterResult> counters(
            @PathVariable long championId,
            @RequestParam String stageKeys,
            @RequestParam String position,
            @RequestParam(defaultValue = "2") int minimumGames,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.query(
                new ChampionCounterQuery(stages, championId, position, minimumGames)));
    }
}
