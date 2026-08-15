package com.loldatahub.domain.statistics;

import java.math.BigDecimal;

/**
 * 英雄常用选手榜：某个分路上使用该英雄次数达到门槛的选手。
 */
public record ChampionPlayerUsage(
        long sourcePlayerId,
        String playerName,
        String playerAvatar,
        String position,
        long pickCount,
        long winningCount,
        BigDecimal winningRate,
        BigDecimal kda
) {
}
