package com.loldatahub.domain.statistics;

/**
 * 战队详情不存在：战队 ID 无数据或未达到最低样本门槛。映射为 HTTP 404。
 */
public class TeamDetailNotFoundException extends RuntimeException {
    public TeamDetailNotFoundException(String message) {
        super(message);
    }
}
