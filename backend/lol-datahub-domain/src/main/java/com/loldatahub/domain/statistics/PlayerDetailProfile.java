package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 选手详情页顶部的选手基础信息，口径与选手统计列表一致。
 */
public record PlayerDetailProfile(
        Long sourcePlayerId,
        String playerName,
        String playerAvatar,
        List<String> teamNames,
        List<String> positions,
        long matchCount,
        long gameCount
) {
}
