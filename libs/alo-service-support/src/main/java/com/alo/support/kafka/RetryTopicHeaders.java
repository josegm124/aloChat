package com.alo.support.kafka;

import java.nio.charset.StandardCharsets;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

public final class RetryTopicHeaders {
    public static final String RETRY_STAGE = "alo-retry-stage";
    public static final String RETRY_ATTEMPT = "alo-retry-attempt";

    private RetryTopicHeaders() {
    }

    public static String retryStage(Headers headers) {
        return headerValue(headers.lastHeader(RETRY_STAGE));
    }

    public static int retryAttempt(Headers headers) {
        String value = headerValue(headers.lastHeader(RETRY_ATTEMPT));
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    public static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String headerValue(Header header) {
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
