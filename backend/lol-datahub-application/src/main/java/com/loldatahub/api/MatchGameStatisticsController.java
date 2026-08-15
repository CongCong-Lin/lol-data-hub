package com.loldatahub.api;

import com.loldatahub.domain.statistics.MatchGameDetailResult;
import com.loldatahub.domain.statistics.MatchGameQuery;
import com.loldatahub.domain.statistics.MatchGamesResult;
import com.loldatahub.domain.statistics.PlayerGamesResult;
import com.loldatahub.domain.statistics.SortDirection;
import com.loldatahub.domain.statistics.StageKey;
import com.loldatahub.statistics.MatchGameStatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
public class MatchGameStatisticsController {
    private static final int DEFAULT_PLAYER_GAME_LIMIT = 50;

    private final MatchGameStatisticsService service;

    public MatchGameStatisticsController(MatchGameStatisticsService service) {
        this.service = service;
    }

    @GetMapping("/matches")
    ApiResponse<MatchGamesResult> matches(
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "startTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        var query = new MatchGameQuery(stages, sortBy, SortDirection.from(sortDirection), offset, limit);
        return ApiResponse.success(service.queryMatches(query));
    }

    @GetMapping("/matches/{matchId}")
    ApiResponse<MatchGameDetailResult> matchDetail(
            @PathVariable long matchId,
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.queryMatchDetail(stages, matchId));
    }

    @GetMapping("/players/{sourcePlayerId}/games")
    ApiResponse<PlayerGamesResult> playerGames(
            @PathVariable long sourcePlayerId,
            @RequestParam(required = false) String stageKeys,
            @RequestParam(required = false) Long seasonId,
            @RequestParam(required = false) List<Long> stageIds,
            @RequestParam(defaultValue = "50") int limit
    ) {
        List<StageKey> stages = StageKeyParamParser.parse(stageKeys, seasonId, stageIds);
        return ApiResponse.success(service.queryPlayerGames(stages, sourcePlayerId, limit));
    }
}
