package com.alo.contracts.assessment;

import java.util.List;

public record BiasAssessment(
        boolean biasTested,
        String methodology,
        List<String> protectedAttributes,
        String resultsSummary,
        String mitigationActions
) {
}
