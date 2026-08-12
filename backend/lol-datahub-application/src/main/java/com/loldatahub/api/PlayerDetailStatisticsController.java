package com.loldatahub.api;

import com.loldatahub.domain.statistics.PlayerDetailQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.PlayerDetailStatisticsResult;
import com.loldatahub.statistics.PlayerDetailStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class PlayerDetailStatisticsController {
    private final PlayerDetailStatisticsService service;

    public PlayerDetailStatisticsController(PlayerDetailStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/players/{sourcePlayerId}/detail")
    ApiResponse<PlayerDetailStatisticsResult> playerDetail(
            @PathVariable long sourcePlayerId,
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam String position,
            @RequestParam(defaultValue = "5") int minimumMatchCount
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new PlayerDetailQuery(sourcePlayerId, stages, position, minimumMatchCount);
        return ApiResponse.success(service.query(query));
    }
}
