package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单局对局读取行：字段与对局表平铺列一一对应。
 */
public record MatchGameRow(
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
