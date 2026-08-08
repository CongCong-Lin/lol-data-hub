package com.loldatahub.api;

import com.loldatahub.domain.statistics.StageKey;

import java.util.List;

/**
 * 控制器层共享的 StageKey 参数解析器。
 * 同时支持新参数 (stageKeys) 和旧参数 (seasonId + stageIds) 两种互斥输入形式。
 */
final class StageKeyParamParser {

    private StageKeyParamParser() {
    }

    /**
     * 解析并生成 List&lt;StageKey&gt;。
     *
     * @param stageKeysRaw 新参数：如 "237:102,239:28"；null 表示未传
     * @param seasonId     旧参数赛季 ID；null 表示未传
     * @param stageIds     旧参数赛段 ID 列表；null 或空表示未传
     * @return 解析后的 StageKey 列表（已去重排序）
     * @throws IllegalArgumentException 参数冲突、缺失或格式非法时
     */
    static List<StageKey> parse(String stageKeysRaw, Long seasonId, List<Long> stageIds) {
        boolean hasNew = stageKeysRaw != null && !stageKeysRaw.isBlank();
        boolean hasOldSeason = seasonId != null;
        boolean hasOldStages = stageIds != null && !stageIds.isEmpty();

        // 两套参数同时出现 → 400
        if (hasNew && (hasOldSeason || hasOldStages)) {
            throw new IllegalArgumentException("stageKeys 与 seasonId/stageIds 不能同时使用，请选择其中一种查询方式");
        }

        // 新参数
        if (hasNew) {
            return parseStageKeys(stageKeysRaw);
        }

        // 旧参数：seasonId 和 stageIds 必须成对出现
        if (hasOldSeason && hasOldStages) {
            return StageKey.fromSeasonStages(seasonId, stageIds);
        }

        // 半套参数
        if (hasOldSeason && !hasOldStages) {
            throw new IllegalArgumentException("使用旧参数查询时，stageIds 不能为空");
        }
        if (!hasOldSeason && hasOldStages) {
            throw new IllegalArgumentException("使用旧参数查询时，必须同时指定 seasonId");
        }

        // 都没传
        throw new IllegalArgumentException("请指定查询参数：stageKeys（新格式）或 seasonId + stageIds（旧格式）");
    }

    /**
     * 解析 stageKeys 字符串，如 "237:102,239:28"。
     */
    private static List<StageKey> parseStageKeys(String raw) {
        String[] parts = raw.split(",", -1);
        if (parts.length == 0) {
            throw new IllegalArgumentException("stageKeys 不能为空");
        }
        List<StageKey> keys = new java.util.ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                throw new IllegalArgumentException("stageKeys 中不能包含空的赛段键");
            }
            keys.add(StageKey.parse(part));
        }
        if (keys.isEmpty()) {
            throw new IllegalArgumentException("stageKeys 解析结果为空");
        }
        return keys.stream().distinct().sorted().toList();
    }
}
