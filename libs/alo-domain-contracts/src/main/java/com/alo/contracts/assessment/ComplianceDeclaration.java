package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;

public record ComplianceDeclaration(
        List<String> applicableRegulations,
        String complianceStatus,
        String auditType,
        String auditor,
        Instant auditDate
) {
}
