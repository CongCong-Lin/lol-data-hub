package com.loldatahub.domain.statistics;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 不可变的复合赛段标识，由 sourceSeasonId + sourceStageId 组成。
 * 用于跨赛事（不同 sourceSeasonId）的复合查询。
 */
public record StageKey(long sourceSeasonId, long sourceStageId) implements Comparable<StageKey> {
    private static final Pattern CANONICAL_PATTERN = Pattern.compile("([1-9]\\d*):([1-9]\\d*)");

    public StageKey {
        if (sourceSeasonId <= 0) {
            throw new IllegalArgumentException("赛季 ID 必须为正整数");
        }
        if (sourceStageId <= 0) {
            throw new IllegalArgumentException("赛段 ID 必须为正整数");
        }
    }

    /**
     * 返回规范化的 canonical 字符串表示：seasonId:stageId
     */
    public String canonical() {
        return sourceSeasonId + ":" + sourceStageId;
    }

    /**
     * 从 canonical 字符串解析 StageKey。
     * 只接受两个正整数和一个冒号，允许整体 trim。
     */
    public static StageKey parse(String input) {
        if (input == null) {
            throw new IllegalArgumentException("赛段键不能为空");
        }
        String trimmed = input.trim();
        Matcher matcher = CANONICAL_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("赛段键格式非法，期望 'seasonId:stageId'，实际为：" + input);
        }
        try {
            return new StageKey(Long.parseLong(matcher.group(1)), Long.parseLong(matcher.group(2)));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("赛段键中的 ID 超出有效整数范围：" + input);
        }
    }

    /**
     * 按 seasonId 升序、stageId 升序排列。
     */
    @Override
    public int compareTo(StageKey other) {
        int cmp = Long.compare(this.sourceSeasonId, other.sourceSeasonId);
        if (cmp != 0) return cmp;
        return Long.compare(this.sourceStageId, other.sourceStageId);
    }

    /**
     * 从旧的 (seasonId, stageIds) 形式批量生成 StageKey 列表，去重并排序。
     */
    public static List<StageKey> fromSeasonStages(long seasonId, List<Long> stageIds) {
        if (stageIds == null || stageIds.isEmpty()) {
            return List.of();
        }
        return stageIds.stream()
                .distinct()
                .map(stageId -> new StageKey(seasonId, stageId))
                .sorted()
                .toList();
    }
}
