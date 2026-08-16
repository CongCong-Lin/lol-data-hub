package com.loldatahub.infrastructure.model;

/**
 * 英雄目录精简行：版本窗口对比展示名称与头像。
 */
public record ChampionCatalogRow(
        long sourceChampionId,
        String internalName,
        String chineseName,
        String logoUrl
) {
}
