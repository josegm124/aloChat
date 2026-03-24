package com.alo.contracts.assessment;

import java.time.Instant;

public record GeneratedAssessmentReport(
        String reportId,
        String assessmentId,
        PreferredLanguage preferredLanguage,
        WebAssessmentReport webReport,
        PdfReportArtifact pdfReportArtifact,
        Instant generatedAt
) {
}
