package com.loldatahub.api;

import com.loldatahub.domain.statistics.EloRatingQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.EloRatingResult;
import com.loldatahub.statistics.EloRatingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class EloRatingController {
    private final EloRatingService service;

    public EloRatingController(EloRatingService service) {
        this.service = service;
    }

    @GetMapping("/elo")
    ApiResponse<EloRatingResult> elo(
            @RequestParam String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.query(new EloRatingQuery(stages)));
    }
}
