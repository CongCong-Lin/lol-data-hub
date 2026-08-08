package com.loldatahub.collector;

import com.loldatahub.infrastructure.mapper.CollectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class CollectionRunRecovery {
    private static final Logger log = LoggerFactory.getLogger(CollectionRunRecovery.class);

    private final CollectionMapper collectionMapper;

    public CollectionRunRecovery(CollectionMapper collectionMapper) {
        this.collectionMapper = collectionMapper;
    }

    @PostConstruct
    void recoverStaleRuns() {
        int recovered = collectionMapper.recoverStaleRunningRuns();
        if (recovered > 0) {
            log.warn("应用启动回收：将 {} 个悬挂采集任务标记为 FAILED", recovered);
        } else {
            log.info("应用启动回收：无悬挂采集任务需要回收");
        }
    }
}
