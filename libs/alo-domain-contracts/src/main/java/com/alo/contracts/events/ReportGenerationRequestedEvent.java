package com.alo.contracts.events;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;

import java.time.Instant;

public record ReportGenerationRequestedEvent(
        String eventId,
        String traceId,
        ConsolidatedAssessmentResult consolidatedAssessmentResult,
        Instant publishedAt
) {
}
