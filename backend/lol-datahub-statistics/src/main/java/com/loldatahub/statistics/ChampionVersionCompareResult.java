package com.loldatahub.statistics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 版本窗口对比结果：两个日期时点的英雄出场与胜率差异。
 */
public record ChampionVersionCompareResult(
        LocalDate fromDate,
        LocalDate toDate,
        List<Item> items
) {
    /** 单个英雄的窗口对比；winRate 为胜率（0-1），pickDelta 为出场次数差（to - from）。 */
    public record Item(
            long championId,
            String championName,
            String championChineseName,
            String championLogo,
            long fromPickCount,
            long toPickCount,
            long pickDelta,
            BigDecimal fromWinRate,
            BigDecimal toWinRate,
            BigDecimal winRateDelta
    ) {
    }
}
