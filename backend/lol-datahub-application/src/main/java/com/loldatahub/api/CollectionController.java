package com.loldatahub.api;

import com.loldatahub.collector.CollectionResult;
import com.loldatahub.collector.HeroCollectionService;
import com.loldatahub.collector.PlayerCollectionService;
import com.loldatahub.collector.TeamCollectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/internal/collections")
public class CollectionController {
    private final HeroCollectionService heroCollectionService;
    private final TeamCollectionService teamCollectionService;
    private final PlayerCollectionService playerCollectionService;

    public CollectionController(HeroCollectionService heroCollectionService,
                                TeamCollectionService teamCollectionService,
                                PlayerCollectionService playerCollectionService) {
        this.heroCollectionService = heroCollectionService;
        this.teamCollectionService = teamCollectionService;
        this.playerCollectionService = playerCollectionService;
    }

    @PostMapping("/heroes")
    ApiResponse<CollectionResult> collectHeroes(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.success(heroCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    @PostMapping("/teams")
    ApiResponse<CollectionResult> collectTeams(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.success(teamCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    @PostMapping("/players")
    ApiResponse<CollectionResult> collectPlayers(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.success(playerCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    public record CollectionRequest(
            @Positive long seasonId,
            @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> stageIds
    ) {
    }
}

