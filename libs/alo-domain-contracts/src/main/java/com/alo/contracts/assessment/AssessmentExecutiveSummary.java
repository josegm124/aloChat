package com.alo.contracts.assessment;

import java.util.List;

public record AssessmentExecutiveSummary(
        String title,
        String summary,
        List<String> topGaps,
        List<String> recommendedActions
) {
}
