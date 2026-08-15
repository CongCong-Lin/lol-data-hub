package com.loldatahub.domain.statistics;

/**
 * 战队选手名单：该战队在所选赛段内出战过的选手及位置/场次。
 */
public record TeamPlayerUsage(
        long sourcePlayerId,
        String playerName,
        String playerAvatar,
        String position,
        long matchCount,
        long gameCount
) {
}
