package com.loldatahub.api;

import com.loldatahub.domain.statistics.ChampionDetailQuery;
import com.loldatahub.domain.statistics.ChampionDetailStatisticsResult;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.ChampionDetailStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class ChampionDetailStatisticsController {
    private final ChampionDetailStatisticsService service;

    public ChampionDetailStatisticsController(ChampionDetailStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/champions/{sourceChampionId}/detail")
    ApiResponse<ChampionDetailStatisticsResult> championDetail(
            @PathVariable long sourceChampionId,
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "5") int minimumPickCount,
            @RequestParam(required = false) String position
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new ChampionDetailQuery(sourceChampionId, stages, minimumPickCount, position);
        return ApiResponse.success(service.query(query));
    }
}
