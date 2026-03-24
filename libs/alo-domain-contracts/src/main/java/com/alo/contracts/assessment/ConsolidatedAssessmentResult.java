package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;

public record ConsolidatedAssessmentResult(
        String assessmentId,
        String submissionId,
        String tenantId,
        String organizationId,
        PreferredLanguage preferredLanguage,
        Sector sector,
        String regulatoryProfileId,
        AssessmentStatus status,
        RiskLevel riskLevel,
        TrustSignal trustSignal,
        int overallScore,
        int totalFindings,
        int compliantCount,
        int partialCount,
        int gapCount,
        int criticalGapCount,
        int totalEvidenceItems,
        List<ApplicableFramework> applicableFrameworks,
        List<ControlFinding> findings,
        List<EvidenceItem> evidenceItems,
        AssessmentExecutiveSummary executiveSummary,
        Instant consolidatedAt
) {
}
