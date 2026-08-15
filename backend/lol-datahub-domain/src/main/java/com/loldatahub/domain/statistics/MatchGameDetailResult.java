package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 单场比赛详情：该场比赛的全部局（BO1/BO3/BO5）与所有局的选手逐局表现。
 * players 按 gameNumber 分组即可还原每局的十人面板。
 */
public record MatchGameDetailResult(
        long dataVersion,
        long sourceMatchId,
        List<MatchGameRecord> games,
        List<MatchGamePlayerRecord> players
) {
}
