package com.loldatahub.domain.statistics;

/**
 * 战队详情页顶部的战队基础信息，口径与战队统计列表一致。
 */
public record TeamDetailProfile(
        long sourceTeamId,
        String teamName,
        String teamLogo,
        long matchCount,
        long gameCount,
        long matchWinCount
) {
}
