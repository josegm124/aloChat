package com.alo.contracts.assessment;

import java.util.List;
import java.util.Map;

public record RegulatoryProfile(
        String regulatoryProfileId,
        Sector sector,
        String geography,
        String aiSystemCategory,
        RiskLevel riskLevel,
        boolean highRiskLikely,
        List<ApplicableFramework> applicableFrameworks,
        List<String> applicableControlPacks,
        List<String> requiredIntakeFields,
        String summary,
        Map<String, String> metadata
) {
}
