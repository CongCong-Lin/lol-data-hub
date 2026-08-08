package com.loldatahub.infrastructure.model;

public record TeamWrite(
        long teamId,
        String name,
        String logoUrl
) {
}
