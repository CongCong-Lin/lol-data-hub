package com.loldatahub.infrastructure.model;

public record PlayerWrite(
        String playerKey,
        Long sourcePlayerId,
        String name,
        String avatarUrl
) {
}
