package com.alo.contracts.events;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.RegulatoryProfile;

import java.time.Instant;

public record ProfileResolvedEvent(
        String eventId,
        String traceId,
        AssessmentEnvelope assessment,
        RegulatoryProfile regulatoryProfile,
        Instant publishedAt
) {
}
