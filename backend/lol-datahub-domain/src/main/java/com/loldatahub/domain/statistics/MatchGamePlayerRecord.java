package com.loldatahub.domain.statistics;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单局对局中的选手表现记录，来自官网 matchDetail 逐局明细。
 * 比例为逐局分子/分母重算值，可能为 null（队伍击杀/伤害/经济为 0 时）。
 */
public record MatchGamePlayerRecord(
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
