package com.alo.report.api;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record FinalizeReportRequest(
        @NotNull @Valid AssessmentEnvelope assessment,
        @NotNull @Valid DocumentAnalysisResult documentAnalysisResult,
        @Valid DatasetAnalysisResult datasetAnalysisResult,
        @NotEmpty List<@Email String> recipients
) {
}
