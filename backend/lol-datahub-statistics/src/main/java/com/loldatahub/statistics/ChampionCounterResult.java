package com.loldatahub.statistics;

import java.math.BigDecimal;
import java.util.List;

/**
 * 英雄对位克制结果：目标英雄在指定分路上的全部对位与胜率。
 */
public record ChampionCounterResult(
        long championId,
        String position,
        long totalGames,
        List<Opponent> opponents
) {
    /** 单个对手英雄的对位数据；winRate 为目标英雄对阵该英雄的胜率。 */
    public record Opponent(
            long championId,
            String championName,
            String championChineseName,
            String championTitle,
            String championLogo,
            long games,
            long wins,
            BigDecimal winRate
    ) {
    }
}
