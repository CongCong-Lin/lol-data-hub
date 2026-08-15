package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单局选手表现读取行。
 */
public record MatchGamePlayerRow(
        long sourceSeasonId,
        long sourceStageId,
        long sourceMatchId,
        int gameNumber,
        LocalDateTime startTime,
        long sourcePlayerId,
        String playerName,
        long sourceTeamId,
        String teamName,
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championTitle,
        String championLogo,
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
        BigDecimal goldPercent
) {
}
