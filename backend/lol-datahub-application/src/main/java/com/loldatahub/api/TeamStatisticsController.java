package com.loldatahub.api;

import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamStatisticsQuery;
import com.loldatahub.statistics.TeamStatisticsResult;
import com.loldatahub.statistics.TeamStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class TeamStatisticsController {
    private final TeamStatisticsService service;

    public TeamStatisticsController(TeamStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/teams")
    ApiResponse<TeamStatisticsResult> teams(
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "3") int minimumMatchCount,
            @RequestParam(defaultValue = "winningRate") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new TeamStatisticsQuery(
                stages, minimumMatchCount, sortBy,
                SortDirection.from(sortDirection)
        );
        return ApiResponse.success(service.query(query));
    }
}
