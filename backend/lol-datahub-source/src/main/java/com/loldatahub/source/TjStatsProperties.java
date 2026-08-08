package com.loldatahub.source;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lol-datahub.source.tjstats")
public record TjStatsProperties(
        String baseUrl,
        String authorization,
        Duration connectTimeout,
        Duration readTimeout
) {
    public TjStatsProperties {
        baseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://open.tjstats.com/match-auth-app/open/v1"
                : baseUrl.replaceAll("/$", "");
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(20) : readTimeout;
    }
}

