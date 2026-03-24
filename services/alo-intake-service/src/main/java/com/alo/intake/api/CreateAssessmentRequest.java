package com.alo.intake.api;

import com.alo.contracts.assessment.HealthcareAuditProfile;
import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.assessment.Sector;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public record CreateAssessmentRequest(
        @NotBlank String tenantId,
        @NotBlank String organizationId,
        @NotBlank String userId,
        @NotNull PreferredLanguage preferredLanguage,
        @NotNull Sector sector,
        @NotBlank String useCaseType,
        @NotBlank String aiSystemCategory,
        @NotBlank String geography,
        boolean datasetProvided,
        @NotBlank String systemName,
        @NotBlank String systemVersion,
        @NotBlank String provider,
        @NotBlank String deploymentContext,
        @NotNull List<@Valid CreateArtifactRequest> artifacts,
        @Valid HealthcareAuditProfile healthcareAuditProfile,
        Map<String, String> intakeAnswers
) {
}
