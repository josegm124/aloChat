package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record HealthcareAuditProfile(
        String systemName,
        String systemVersion,
        String provider,
        String deploymentContext,
        Purpose purpose,
        ModelTraceability modelTraceability,
        DataSourceProfile dataSource,
        PrivacyMeasures privacyMeasures,
        BiasAssessment biasAssessment,
        ExplainabilityProfile explainability,
        MonitoringPolicy monitoringPolicy,
        RemediationMechanism remediationMechanism,
        ComplianceDeclaration complianceDeclaration,
        List<EvidenceItem> evidences,
        Instant createdAt,
        Instant lastUpdated,
        Map<String, String> tags
) {
}
