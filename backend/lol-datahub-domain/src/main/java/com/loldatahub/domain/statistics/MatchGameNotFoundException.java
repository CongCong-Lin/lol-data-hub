package com.loldatahub.domain.statistics;

/** 指定赛段内不存在该比赛的对局明细时抛出。 */
public class MatchGameNotFoundException extends RuntimeException {
    public MatchGameNotFoundException(long matchId) {
        super("所选赛段内不存在比赛 " + matchId + " 的对局明细");
    }
}
