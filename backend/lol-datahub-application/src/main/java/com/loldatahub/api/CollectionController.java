package com.loldatahub.api;

import com.loldatahub.collector.CollectionResult;
import com.loldatahub.collector.HeroCollectionService;
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

    public CollectionController(HeroCollectionService heroCollectionService) {
        this.heroCollectionService = heroCollectionService;
    }

    @PostMapping("/heroes")
    ApiResponse<CollectionResult> collectHeroes(@Valid @RequestBody HeroCollectionRequest request) {
        return ApiResponse.success(heroCollectionService.collect(request.seasonId(), request.stageIds()));
    }

    public record HeroCollectionRequest(
            @Positive long seasonId,
            @NotEmpty List<@Positive Long> stageIds
    ) {
    }
}

