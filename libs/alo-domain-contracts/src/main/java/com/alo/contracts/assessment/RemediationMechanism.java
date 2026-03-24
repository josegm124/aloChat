package com.alo.contracts.assessment;

public record RemediationMechanism(
        boolean fallbackAvailable,
        String fallbackDescription,
        String incidentResponseProcess,
        boolean humanOverrideAllowed
) {
}
