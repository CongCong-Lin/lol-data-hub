package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单局对局记录：双方战队可累加指标在同一行，供对局赛果列表与战队近期对局展示。
 * startTime 为空表示原始响应未提供对局开始时间（回填时按赛段开始时间近似）。
 */
public record MatchGameRecord(
        long sourceSeasonId,
        long sourceStageId,
        long sourceMatchId,
        int gameNumber,
        LocalDateTime startTime,
        long teamAId,
        String teamAName,
        String teamALogo,
        long teamAKills,
        long teamAAssists,
        BigDecimal teamADamage,
        BigDecimal teamAGold,
        long teamAWardsPlaced,
        long teamAWardsKilled,
        long teamAMinionKills,
        long teamADragons,
        long teamABarons,
        long teamATurrets,
        boolean teamAFirstBlood,
        long teamBId,
        String teamBName,
        String teamBLogo,
        long teamBKills,
        long teamBAssists,
        BigDecimal teamBDamage,
        BigDecimal teamBGold,
        long teamBWardsPlaced,
        long teamBWardsKilled,
        long teamBMinionKills,
        long teamBDragons,
        long teamBBarons,
        long teamBTurrets,
        boolean teamBFirstBlood,
        long winnerTeamId,
        long gameDurationSeconds
) {
}
