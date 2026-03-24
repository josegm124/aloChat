package com.alo.report.api;

import com.alo.contracts.assessment.ConsolidatedAssessmentResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record GenerateReportRequest(
        @NotNull @Valid ConsolidatedAssessmentResult consolidatedAssessmentResult
) {
}
