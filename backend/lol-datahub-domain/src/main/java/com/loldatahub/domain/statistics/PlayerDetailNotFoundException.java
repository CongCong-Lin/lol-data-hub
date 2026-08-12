package com.loldatahub.domain.statistics;

/**
 * 选手详情不存在：选手 ID 无数据、在所选位置没有数据，或未达到最低样本门槛。
 * 映射为 HTTP 404，message 区分具体原因。
 */
public class PlayerDetailNotFoundException extends RuntimeException {
    public PlayerDetailNotFoundException(String message) {
        super(message);
    }
}
