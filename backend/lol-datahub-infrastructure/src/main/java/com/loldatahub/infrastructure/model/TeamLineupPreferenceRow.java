package com.loldatahub.infrastructure.model;

/**
 * 战队阵容偏好读取行：某个分路上使用某英雄的选取次数与胜场。
 * position 由 team_game_lineup_current 的五个英雄列展开而来。
 */
public record TeamLineupPreferenceRow(
        long teamId,
        String position,
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championLogo,
        long pickCount,
        long winningCount
) {
}
