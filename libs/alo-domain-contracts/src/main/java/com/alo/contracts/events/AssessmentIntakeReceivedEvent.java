package com.alo.contracts.events;

import com.alo.contracts.assessment.AssessmentEnvelope;

import java.time.Instant;

public record AssessmentIntakeReceivedEvent(
        String eventId,
        String traceId,
        AssessmentEnvelope assessment,
        Instant publishedAt
) {
}
