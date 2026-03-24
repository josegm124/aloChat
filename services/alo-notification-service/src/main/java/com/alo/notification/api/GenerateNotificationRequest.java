package com.alo.notification.api;

import com.alo.contracts.assessment.GeneratedAssessmentReport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateNotificationRequest(
        @NotNull @Valid GeneratedAssessmentReport generatedAssessmentReport,
        @NotBlank String tenantId,
        @NotEmpty List<@Email String> recipients,
        @NotBlank String reportAccessUrl
) {
}
