package com.loldatahub.api;

import com.loldatahub.domain.statistics.ChampionVersionCompareQuery;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.ChampionVersionCompareResult;
import com.loldatahub.statistics.ChampionVersionCompareService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics/champions")
public class ChampionVersionCompareController {
    private final ChampionVersionCompareService service;

    public ChampionVersionCompareController(ChampionVersionCompareService service) {
        this.service = service;
    }

    @GetMapping("/version-compare")
    ApiResponse<ChampionVersionCompareResult> versionCompare(
            @RequestParam String stageKeys,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.query(new ChampionVersionCompareQuery(stages, fromDate, toDate)));
    }
}
