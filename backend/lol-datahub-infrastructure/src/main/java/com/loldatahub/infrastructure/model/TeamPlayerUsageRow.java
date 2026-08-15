package com.loldatahub.infrastructure.model;

/**
 * 战队选手名单读取行：该战队在所选赛段内出战过的选手及位置/场次，
 * 来自对局明细回填表 match_game_player_current。
 */
public record TeamPlayerUsageRow(
        long sourcePlayerId,
        String playerName,
        String playerAvatar,
        String position,
        long matchCount,
        long gameCount
) {
}
