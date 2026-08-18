package com.loldatahub.api;

import com.loldatahub.domain.statistics.ChampionStatisticsQuery;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.ChampionStatisticsResult;
import com.loldatahub.statistics.ChampionStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class ChampionStatisticsController {
    private final ChampionStatisticsService service;

    public ChampionStatisticsController(ChampionStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/champions")
    ApiResponse<ChampionStatisticsResult> champions(
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "5") int minimumPickCount,
            @RequestParam(required = false) String position,
            @RequestParam(defaultValue = "bpRate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new ChampionStatisticsQuery(
                stages, minimumPickCount, position, sortBy,
                SortDirection.from(sortDirection)
        );
        return ApiResponse.success(service.query(query));
    }
}
