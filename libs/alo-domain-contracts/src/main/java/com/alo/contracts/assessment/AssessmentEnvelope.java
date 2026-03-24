package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AssessmentEnvelope(
        String assessmentId,
        String submissionId,
        String tenantId,
        String organizationId,
        String userId,
        String traceId,
        String idempotencyKey,
        PreferredLanguage preferredLanguage,
        Sector sector,
        String useCaseType,
        String aiSystemCategory,
        String geography,
        String regulatoryProfileId,
        boolean datasetProvided,
        List<ArtifactRef> artifacts,
        AssessmentStatus status,
        Instant createdAt,
        Instant updatedAt,
        Map<String, String> metadata
) {
}
