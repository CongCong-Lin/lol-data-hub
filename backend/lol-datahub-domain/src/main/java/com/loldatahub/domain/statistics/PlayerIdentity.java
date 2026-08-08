package com.loldatahub.domain.statistics;

import java.util.Locale;

public final class PlayerIdentity {
    private PlayerIdentity() {
    }

    public static String resolve(Long playerId, String playerName) {
        if (playerId != null && playerId > 0) {
            return "id:" + playerId;
        }
        if (playerName == null || playerName.isBlank()) {
            throw new IllegalArgumentException("选手名称不能为空且 playerId 无效");
        }
        return "name:" + playerName.trim().toLowerCase(Locale.ROOT);
    }
}
