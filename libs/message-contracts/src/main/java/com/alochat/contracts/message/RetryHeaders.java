package com.alochat.contracts.message;

public final class RetryHeaders {

    public static final String TARGET_SERVICE = "x-target-service";
    public static final String SOURCE_TOPIC = "x-source-topic";
    public static final String RETRY_STAGE = "x-retry-stage";
    public static final String RETRY_COUNT = "x-retry-count";
    public static final String FAILURE_REASON = "x-failure-reason";

    private RetryHeaders() {
    }
}
