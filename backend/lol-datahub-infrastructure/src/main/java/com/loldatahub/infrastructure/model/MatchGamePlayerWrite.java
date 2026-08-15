package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * 单局对局中的选手表现写入记录。
 */
public record MatchGamePlayerWrite(
        long runId,
        long seasonId,
        long stageId,
        long matchId,
        int gameNumber,
        LocalDateTime startTime,
        long playerId,
        long teamId,
        long championId,
        String position,
        boolean won,
        long kills,
        long deaths,
        long assists,
        BigDecimal heroDamage,
        BigDecimal playerGold,
        long teamKills,
        BigDecimal teamDamage,
        BigDecimal teamGold,
        BigDecimal killParticipantPercent,
        BigDecimal damagePercent,
        BigDecimal goldPercent,
        OffsetDateTime collectedAt
) {
}
