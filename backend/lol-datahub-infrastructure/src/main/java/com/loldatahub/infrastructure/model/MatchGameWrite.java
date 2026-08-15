package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 单局对局写入记录：双方战队可累加指标平铺在同一行。
 */
public record MatchGameWrite(
        long runId,
        long seasonId,
        long stageId,
        long matchId,
        int gameNumber,
        LocalDateTime startTime,
        long teamAId,
        long teamBId,
        long winTeamId,
        long gameDurationSeconds,
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
        OffsetDateTime collectedAt
) {
}
