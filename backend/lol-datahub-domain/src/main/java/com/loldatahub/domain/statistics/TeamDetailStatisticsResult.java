package com.loldatahub.domain.statistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 战队详情页数据：核心指标与排名、阵容偏好、选手名单、近期对局。
 */
public record TeamDetailStatisticsResult(
        long dataVersion,
        int minimumMatchCount,
        int cohortSize,
        TeamDetailProfile team,
        List<RankedTeamMetric> coreMetrics,
        List<TeamLineupPreference> lineupPreferences,
        List<TeamPlayerUsage> players,
        List<MatchGameRecord> recentGames,
        LocalDateTime latestCollectedAt
) {
}
