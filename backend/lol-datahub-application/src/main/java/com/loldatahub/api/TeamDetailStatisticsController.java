package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamDetailQuery;
import com.loldatahub.domain.statistics.TeamDetailStatisticsResult;
import com.loldatahub.statistics.TeamDetailStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class TeamDetailStatisticsController {
    private final TeamDetailStatisticsService service;

    public TeamDetailStatisticsController(TeamDetailStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/teams/{sourceTeamId}/detail")
    ApiResponse<TeamDetailStatisticsResult> teamDetail(
            @PathVariable long sourceTeamId,
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "3") int minimumMatchCount
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new TeamDetailQuery(sourceTeamId, stages, minimumMatchCount);
        return ApiResponse.success(service.query(query));
    }
}
