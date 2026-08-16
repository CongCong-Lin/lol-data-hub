package com.loldatahub.infrastructure.model;

/**
 * 英雄对位克制聚合行：对手英雄与其对位场次、被击败场次。
 */
public record ChampionCounterRow(
        long opponentChampionId,
        String championName,
        String championChineseName,
        String championTitle,
        String championLogo,
        long games,
        long wins
) {
}
