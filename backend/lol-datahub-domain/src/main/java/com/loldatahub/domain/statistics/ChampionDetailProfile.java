package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 英雄详情页顶部的英雄基础信息。
 */
public record ChampionDetailProfile(
        long sourceChampionId,
        String championName,
        String championChineseName,
        String championTitle,
        String championLogo,
        List<String> positions
) {
}
