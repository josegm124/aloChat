package com.alo.contracts.assessment;

public record ExplainabilityProfile(
        boolean explainable,
        String techniquesUsed,
        String explanationAudience,
        boolean humanReadableOutput
) {
}
