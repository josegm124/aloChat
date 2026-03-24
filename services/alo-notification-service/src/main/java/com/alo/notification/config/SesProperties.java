package com.alo.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alo.notification.ses")
public record SesProperties(
        String fromAddress
) {
}
