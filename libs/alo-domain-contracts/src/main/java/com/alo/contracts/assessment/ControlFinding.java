package com.alo.contracts.assessment;

import java.util.List;

public record ControlFinding(
        String findingId,
        String assessmentId,
        ApplicableFramework framework,
        CompliancePillar pillar,
        String controlId,
        String controlTitle,
        ControlFindingStatus status,
        FindingSeverity severity,
        String rationale,
        List<String> evidenceRefs,
        List<String> recommendedActions
) {
}
