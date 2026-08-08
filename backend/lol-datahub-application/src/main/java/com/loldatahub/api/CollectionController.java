package com.loldatahub.api;

import com.loldatahub.collector.CollectionResult;
import com.loldatahub.collector.HeroCollectionService;
import com.loldatahub.collector.TeamCollectionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
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

    public CollectionController(HeroCollectionService heroCollectionService,
                                TeamCollectionService teamCollectionService) {
        this.heroCollectionService = heroCollectionService;
        this.teamCollectionService = teamCollectionService;
    }

    @PostMapping("/heroes")
    ApiResponse<CollectionResult> collectHeroes(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.success(heroCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    @PostMapping("/teams")
    ApiResponse<CollectionResult> collectTeams(@Valid @RequestBody CollectionRequest request) {
        return ApiResponse.success(teamCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    public record CollectionRequest(
            @Positive long seasonId,
            @NotEmpty List<@Positive Long> stageIds
    ) {
    }
}

