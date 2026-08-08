package com.loldatahub.source;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
@EnableConfigurationProperties(TjStatsProperties.class)
public class TjStatsSourceConfiguration {

    @Bean
    RestClient tjStatsRestClient(TjStatsProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);
        if (properties.authorization() != null && !properties.authorization().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, properties.authorization());
        }
        return builder.build();
    }

    @Bean
    TjStatsResponseParser tjStatsResponseParser(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        return new TjStatsResponseParser(objectMapper);
    }
}

