package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.domain.statistics.TeamHeadToHeadQuery;
import com.loldatahub.statistics.TeamHeadToHeadResult;
import com.loldatahub.statistics.TeamHeadToHeadStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics/teams")
public class TeamHeadToHeadStatisticsController {
    private final TeamHeadToHeadStatisticsService service;

    public TeamHeadToHeadStatisticsController(TeamHeadToHeadStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/{teamId}/head-to-head")
    ApiResponse<TeamHeadToHeadResult> headToHead(
            @PathVariable long teamId,
            @RequestParam String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.query(new TeamHeadToHeadQuery(stages, teamId)));
    }
}
