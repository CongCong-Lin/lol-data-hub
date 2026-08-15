package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 战队阵容偏好：某个分路上使用某英雄的选取次数与胜率。
 * 事实表为 team_game_lineup_current 的单局完整阵容。
 */
public record TeamLineupPreference(
        String position,
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championLogo,
        long pickCount,
        BigDecimal pickRate,
        long winningCount,
        BigDecimal winningRate
) {
}
