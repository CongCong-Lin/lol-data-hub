package com.loldatahub.domain.statistics;

/**
 * 英雄详情不存在：英雄 ID 无数据或未达到最低样本门槛。映射为 HTTP 404。
 */
public class ChampionDetailNotFoundException extends RuntimeException {
    public ChampionDetailNotFoundException(String message) {
        super(message);
    }
}
