package com.loldatahub.api;

import com.loldatahub.collector.CatalogCollectionService;
import com.loldatahub.domain.catalog.Season;
import com.loldatahub.domain.catalog.Stage;
import com.loldatahub.infrastructure.mapper.CatalogMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogController {
    private final CatalogMapper catalogMapper;
    private final CatalogCollectionService collectionService;

    public CatalogController(CatalogMapper catalogMapper, CatalogCollectionService collectionService) {
        this.catalogMapper = catalogMapper;
        this.collectionService = collectionService;
    }

    @GetMapping("/seasons")
    ApiResponse<List<Season>> seasons() {
        return ApiResponse.success(catalogMapper.findSeasons());
    }

    @GetMapping("/stages")
    ApiResponse<List<Stage>> stages(@RequestParam long seasonId) {
        return ApiResponse.success(catalogMapper.findStages(seasonId));
    }

    @PostMapping("/sync")
    ApiResponse<CatalogCollectionService.CatalogSyncResult> sync(@RequestParam(required = false) Long seasonId) {
        return ApiResponse.success(collectionService.sync(seasonId));
    }
}

