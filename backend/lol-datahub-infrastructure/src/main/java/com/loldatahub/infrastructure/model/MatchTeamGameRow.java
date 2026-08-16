package com.loldatahub.infrastructure.model;

import java.time.LocalDateTime;

/**
 * 交锋与 Elo 计算使用的单局精简行：只保留队伍、胜方与时间信息。
 */
public record MatchTeamGameRow(
        long sourceSeasonId,
        long sourceStageId,
        long sourceMatchId,
        int gameNumber,
        LocalDateTime startTime,
        long teamAId,
        String teamAName,
        String teamALogo,
        long teamBId,
        String teamBName,
        String teamBLogo,
        long winnerTeamId
) {
}
