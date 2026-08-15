package com.loldatahub.infrastructure.model;

/**
 * source_raw_response 中最近一次保存的 matchDetail 响应（按 matchId 取最新版本）。
 */
public record MatchDetailSourceRow(
        long id,
        long matchId,
        String body
) {
}
