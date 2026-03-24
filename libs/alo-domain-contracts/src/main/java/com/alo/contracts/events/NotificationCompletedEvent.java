package com.alo.contracts.events;

import com.alo.contracts.assessment.GeneratedNotification;

import java.time.Instant;

public record NotificationCompletedEvent(
        String eventId,
        String traceId,
        GeneratedNotification generatedNotification,
        Instant publishedAt
) {
}
