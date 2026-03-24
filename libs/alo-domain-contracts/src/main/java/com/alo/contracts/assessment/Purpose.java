package com.alo.contracts.assessment;

public record Purpose(
        String clinicalUseCase,
        String description,
        RiskLevel riskLevel,
        boolean impactsPatientCare
) {
}
