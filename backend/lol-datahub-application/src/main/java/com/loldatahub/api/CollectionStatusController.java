package com.loldatahub.api;

import com.loldatahub.infrastructure.mapper.CollectionMapper;
import com.loldatahub.infrastructure.model.CollectionStatusRow;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据采集状态查询：最近一次各类型采集任务的执行结果。
 * 只读查询，不触发任何采集动作。
 */
@RestController
@RequestMapping("/api/v1/collections")
public class CollectionStatusController {
    private static final int MAX_LIMIT = 100;

    private final CollectionMapper mapper;

    public CollectionStatusController(CollectionMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping("/status")
    ApiResponse<List<CollectionStatusRow>> status(@RequestParam(defaultValue = "20") int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("查询条数必须是 1 到 " + MAX_LIMIT + " 之间的整数");
        }
        return ApiResponse.success(mapper.findRecentRuns(limit));
    }
}
