package com.loldatahub.source.model;

/**
 * 官网比赛详情中某一局、某一选手的真实登场位置。
 */
public record MatchPlayerPositionSourceRecord(
        long matchId,
        long bo,
        long playerId,
        String position
) {
}
