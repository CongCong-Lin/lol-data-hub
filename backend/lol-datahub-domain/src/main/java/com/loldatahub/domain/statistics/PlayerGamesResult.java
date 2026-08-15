package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 选手单局战绩结果。
 */
public record PlayerGamesResult(
        long dataVersion,
        long sourcePlayerId,
        String playerName,
        List<PlayerGameRecord> items
) {
}
