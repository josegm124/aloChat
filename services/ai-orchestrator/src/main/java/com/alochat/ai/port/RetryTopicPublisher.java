package com.alochat.ai.port;

import com.alochat.contracts.message.MessageEnvelope;

public interface RetryTopicPublisher {

    void publishShortRetry(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable);

    void publishLongRetry(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable);

    void publishDlq(MessageEnvelope envelope, String sourceTopic, String targetService, Throwable throwable);
}
