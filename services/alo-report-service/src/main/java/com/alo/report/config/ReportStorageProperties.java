package com.alo.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "alo.report.storage")
public record ReportStorageProperties(
        String bucket,
        String publicBaseUrl
) {
}
