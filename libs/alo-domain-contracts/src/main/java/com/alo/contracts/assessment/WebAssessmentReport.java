package com.alo.contracts.assessment;

import java.util.List;

public record WebAssessmentReport(
        String title,
        String subtitle,
        String htmlContent,
        List<ReportSection> sections
) {
}
