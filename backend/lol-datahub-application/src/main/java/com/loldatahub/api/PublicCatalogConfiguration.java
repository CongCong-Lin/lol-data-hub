package com.loldatahub.api;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PublicCatalogProperties.class)
class PublicCatalogConfiguration {
}
