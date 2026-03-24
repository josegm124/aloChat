package com.alo.contracts.events;

import com.alo.contracts.assessment.GeneratedAssessmentReport;

import java.time.Instant;
import java.util.List;

public record NotificationGenerationRequestedEvent(
        String eventId,
        String traceId,
        GeneratedAssessmentReport generatedAssessmentReport,
        String tenantId,
        List<String> recipients,
        String reportAccessUrl,
        Instant publishedAt
) {
}
