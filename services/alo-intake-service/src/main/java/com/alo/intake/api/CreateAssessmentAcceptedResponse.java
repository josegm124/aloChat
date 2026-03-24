package com.alo.intake.api;

import com.alo.contracts.assessment.AssessmentEnvelope;

public record CreateAssessmentAcceptedResponse(
        String assessmentId,
        String submissionId,
        String traceId,
        String idempotencyKey,
        String regulatoryProfileId,
        String status,
        AssessmentEnvelope assessment
) {
}
