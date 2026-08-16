package com.loldatahub.statistics;

import java.util.List;

/**
 * 战队 Elo 评分结果：按开赛时间重放全部小局后的当前评分与历史轨迹。
 */
public record EloRatingResult(
        long totalGames,
        List<TeamRating> ratings
) {
    /** 单支战队的评分。ratingHistory 为该队每打完一局后的评分序列（起始 1500 之前不含）。 */
    public record TeamRating(
            long teamId,
            String teamName,
            String teamLogo,
            int rating,
            int rank,
            long games,
            long wins,
            long losses,
            List<Double> ratingHistory
    ) {
    }
}
