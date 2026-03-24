package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;

public record GeneratedNotification(
        String notificationId,
        String assessmentId,
        String tenantId,
        PreferredLanguage preferredLanguage,
        String channel,
        List<String> recipients,
        String subject,
        String body,
        String reportAccessUrl,
        String deliveryStatus,
        String providerMessageId,
        Instant generatedAt
) {
}
