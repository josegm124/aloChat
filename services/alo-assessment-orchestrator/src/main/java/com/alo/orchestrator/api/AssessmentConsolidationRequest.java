package com.alo.orchestrator.api;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record AssessmentConsolidationRequest(
        @NotNull @Valid AssessmentEnvelope assessment,
        @NotNull @Valid RegulatoryProfile regulatoryProfile,
        @NotNull @Valid DocumentAnalysisResult documentAnalysisResult,
        @Valid DatasetAnalysisResult datasetAnalysisResult
) {
}
