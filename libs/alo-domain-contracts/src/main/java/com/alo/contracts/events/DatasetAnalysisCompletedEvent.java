package com.alo.contracts.events;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;

import java.time.Instant;

public record DatasetAnalysisCompletedEvent(
        String eventId,
        String traceId,
        AssessmentEnvelope assessment,
        RegulatoryProfile regulatoryProfile,
        DatasetAnalysisResult result,
        Instant publishedAt
) {
}
