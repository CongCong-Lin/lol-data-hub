package com.loldatahub.domain.statistics;

import java.util.List;

/**
 * 对局赛果列表结果，含分页信息与数据版本。
 */
public record MatchGamesResult(
        long dataVersion,
        long total,
        int offset,
        int limit,
        List<MatchGameRecord> items
) {
}
