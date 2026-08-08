package com.loldatahub.api;

import com.loldatahub.domain.statistics.PlayerStatisticsQuery;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.PlayerStatisticsResult;
import com.loldatahub.statistics.PlayerStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class PlayerStatisticsController {
    private final PlayerStatisticsService service;

    public PlayerStatisticsController(PlayerStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/players")
    ApiResponse<PlayerStatisticsResult> players(
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "5") int minimumMatchCount,
            @RequestParam(required = false) String position,
            @RequestParam(defaultValue = "kda") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new PlayerStatisticsQuery(
                stages, minimumMatchCount, position, sortBy,
                SortDirection.from(sortDirection)
        );
        return ApiResponse.success(service.query(query));
    }
}
