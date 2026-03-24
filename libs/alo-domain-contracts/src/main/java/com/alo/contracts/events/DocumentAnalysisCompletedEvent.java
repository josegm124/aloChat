package com.alo.contracts.events;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;

import java.time.Instant;

public record DocumentAnalysisCompletedEvent(
        String eventId,
        String traceId,
        AssessmentEnvelope assessment,
        RegulatoryProfile regulatoryProfile,
        DocumentAnalysisResult result,
        Instant publishedAt
) {
}
