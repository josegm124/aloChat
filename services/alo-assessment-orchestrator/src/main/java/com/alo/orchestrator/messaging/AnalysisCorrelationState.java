package com.alo.orchestrator.messaging;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.assessment.RegulatoryProfile;

public record AnalysisCorrelationState(
        AssessmentEnvelope assessment,
        RegulatoryProfile regulatoryProfile,
        DocumentAnalysisResult documentAnalysisResult,
        DatasetAnalysisResult datasetAnalysisResult
) {
    public AnalysisCorrelationState withDocument(DocumentAnalysisResult result) {
        return new AnalysisCorrelationState(assessment, regulatoryProfile, result, datasetAnalysisResult);
    }

    public AnalysisCorrelationState withDataset(DatasetAnalysisResult result) {
        return new AnalysisCorrelationState(assessment, regulatoryProfile, documentAnalysisResult, result);
    }

    public boolean isReady() {
        if (documentAnalysisResult == null) {
            return false;
        }
        return !assessment.datasetProvided() || datasetAnalysisResult != null;
    }
}
