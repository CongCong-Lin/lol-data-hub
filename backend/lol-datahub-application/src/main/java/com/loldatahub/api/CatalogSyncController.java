package com.loldatahub.api;

import com.loldatahub.collector.CatalogCollectionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/catalog")
public class CatalogSyncController {
    private final CatalogCollectionService collectionService;

    public CatalogSyncController(CatalogCollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping("/sync")
    ApiResponse<CatalogCollectionService.CatalogSyncResult> sync(@RequestParam(required = false) Long seasonId) {
        return ApiResponse.success(collectionService.sync(seasonId));
    }
}
