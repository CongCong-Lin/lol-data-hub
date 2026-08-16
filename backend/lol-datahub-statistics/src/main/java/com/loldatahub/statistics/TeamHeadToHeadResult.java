package com.loldatahub.statistics;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 战队交锋记录：与每个对手的系列赛/小局胜负汇总 + 最近交手明细。
 */
public record TeamHeadToHeadResult(
        long teamId,
        List<Opponent> opponents,
        List<Meeting> recentMeetings
) {
    /** 单个对手的交锋汇总。 */
    public record Opponent(
            long opponentTeamId,
            String opponentTeamName,
            String opponentTeamLogo,
            long matchCount,
            long matchWins,
            long matchLosses,
            long gameCount,
            long gameWins,
            long gameLosses
    ) {
    }

    /** 一场系列赛交手（战队视角的分局比分）。 */
    public record Meeting(
            long matchId,
            long opponentTeamId,
            String opponentTeamName,
            String opponentTeamLogo,
            LocalDateTime startTime,
            long teamGameWins,
            long opponentGameWins,
            boolean won
    ) {
    }
}
