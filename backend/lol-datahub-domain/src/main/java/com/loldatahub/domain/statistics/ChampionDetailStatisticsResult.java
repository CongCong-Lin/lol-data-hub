package com.loldatahub.domain.statistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 英雄详情页数据：整体 BP/胜负指标、分路统计、常用选手榜、按赛段趋势。
 */
public record ChampionDetailStatisticsResult(
        long dataVersion,
        int minimumPickCount,
        String position,
        ChampionDetailProfile champion,
        ChampionStatistics overall,
        List<ChampionPositionStat> positionStats,
        List<ChampionPlayerUsage> topPlayers,
        List<ChampionTrendPoint> trends,
        LocalDateTime latestCollectedAt
) {
}
