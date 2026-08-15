package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 单场比赛详情：对局记录 + 全部选手的逐局表现。
 */
public record MatchGameDetailResult(
        long dataVersion,
        MatchGameRecord game,
        List<MatchGamePlayerRecord> players
) {
}
