package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 选手单局战绩行：选手在所选赛段内每局的表现与对阵信息。
 * 对手战队名来自同局另一支战队；kda 由后端按 (击杀+助攻)/max(死亡,1) 计算。
 */
public record PlayerGameRecord(
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
        BigDecimal kda,
        BigDecimal heroDamage,
        BigDecimal killParticipantPercent,
        BigDecimal damagePercent
) {
}
