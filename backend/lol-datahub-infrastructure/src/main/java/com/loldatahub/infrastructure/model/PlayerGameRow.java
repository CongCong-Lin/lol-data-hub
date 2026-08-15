package com.loldatahub.infrastructure.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选手单局战绩读取行：含对手战队名与赛段名。
 */
public record PlayerGameRow(
        long sourceSeasonId,
        long sourceStageId,
        String stageName,
        long sourceMatchId,
        int gameNumber,
        LocalDateTime startTime,
        String opponentTeamName,
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championLogo,
        String position,
        boolean won,
        long kills,
        long deaths,
        long assists,
        BigDecimal heroDamage,
        BigDecimal killParticipantPercent,
        BigDecimal damagePercent
) {
}
