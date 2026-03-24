package com.alo.contracts.events;

import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.RegulatoryProfile;

import java.time.Instant;

public record DatasetAnalysisRequestedEvent(
        String eventId,
        String traceId,
        AssessmentEnvelope assessment,
        RegulatoryProfile regulatoryProfile,
        ArtifactRef artifact,
        Instant publishedAt
) {
}
