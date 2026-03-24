package com.alo.report.api;

import com.alo.contracts.assessment.GeneratedAssessmentReport;

public record PublishedReportResponse(
        GeneratedAssessmentReport generatedAssessmentReport,
        String reportAccessUrl,
        String pdfDownloadUrl
) {
}
