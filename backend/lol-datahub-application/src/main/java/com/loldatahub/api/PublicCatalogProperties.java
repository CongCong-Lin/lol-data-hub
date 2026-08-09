package com.loldatahub.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 公共查询页面允许展示的赛事与赛段。
 *
 * <p>官网目录会同时返回其他联赛、特殊赛制和已经下线的赛段。本配置只影响公共目录，
 * 不删除已经同步到数据库的原始目录数据。</p>
 */
@ConfigurationProperties(prefix = "lol-datahub.catalog")
public record PublicCatalogProperties(List<VisibleEvent> visibleEvents) {

    public PublicCatalogProperties {
        visibleEvents = visibleEvents == null ? List.of() : List.copyOf(visibleEvents);
        Set<Long> seasonIds = new HashSet<>();
        for (VisibleEvent event : visibleEvents) {
            if (!seasonIds.add(event.seasonId())) {
                throw new IllegalArgumentException("公共目录中的赛事 ID 不能重复: " + event.seasonId());
            }
        }
    }

    public boolean containsSeason(long seasonId) {
        return visibleEvents.stream().anyMatch(event -> event.seasonId() == seasonId);
    }

    public boolean containsStage(long seasonId, long stageId) {
        return visibleEvents.stream()
                .filter(event -> event.seasonId() == seasonId)
                .anyMatch(event -> event.stageIds().contains(stageId));
    }

    public record VisibleEvent(long seasonId, List<Long> stageIds) {
        public VisibleEvent {
            if (seasonId <= 0) {
                throw new IllegalArgumentException("公共目录中的赛事 ID 必须大于 0");
            }
            if (stageIds == null || stageIds.isEmpty()) {
                throw new IllegalArgumentException("公共目录中的赛事必须至少配置一个赛段: " + seasonId);
            }
            stageIds = stageIds.stream().distinct().toList();
            if (stageIds.stream().anyMatch(stageId -> stageId == null || stageId <= 0)) {
                throw new IllegalArgumentException("公共目录中的赛段 ID 必须大于 0: " + seasonId);
            }
        }
    }
}
