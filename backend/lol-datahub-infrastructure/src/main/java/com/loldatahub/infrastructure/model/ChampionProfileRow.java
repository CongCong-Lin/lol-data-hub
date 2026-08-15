package com.loldatahub.infrastructure.model;

/**
 * 英雄目录基础信息读取行。
 */
public record ChampionProfileRow(
        String internalName,
        String chineseName,
        String chineseTitle,
        String logoUrl
) {
}
