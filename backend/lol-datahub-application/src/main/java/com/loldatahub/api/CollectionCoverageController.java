package com.loldatahub.api;

import com.loldatahub.statistics.CollectionCoverageResult;
import com.loldatahub.statistics.CollectionCoverageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collections")
public class CollectionCoverageController {
    private final CollectionCoverageService service;

    public CollectionCoverageController(CollectionCoverageService service) {
        this.service = service;
    }

    @GetMapping("/coverage")
    ApiResponse<CollectionCoverageResult> coverage() {
        return ApiResponse.success(service.query());
    }
}
