package com.loldatahub.api;

import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamCombinationStatisticsQuery;
import com.loldatahub.domain.statistics.TeamCombinationType;
import com.loldatahub.statistics.TeamCombinationStatisticsResult;
import com.loldatahub.statistics.TeamCombinationStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class TeamCombinationStatisticsController {
    private final TeamCombinationStatisticsService service;

    public TeamCombinationStatisticsController(TeamCombinationStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/team-combinations")
    ApiResponse<TeamCombinationStatisticsResult> combinations(
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "MID_JUNGLE") String combinationType,
            @RequestParam(defaultValue = "3") int minimumPickCount,
            @RequestParam(defaultValue = "pickCount") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new TeamCombinationStatisticsQuery(
                stages, TeamCombinationType.from(combinationType), minimumPickCount,
                sortBy, SortDirection.from(sortDirection));
        return ApiResponse.success(service.query(query));
    }
}
