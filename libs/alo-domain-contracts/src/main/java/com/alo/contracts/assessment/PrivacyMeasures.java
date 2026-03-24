package com.alo.contracts.assessment;

public record PrivacyMeasures(
        boolean gdprCompliant,
        String anonymizationMethod,
        String dataRetentionPolicy,
        String accessControl,
        boolean encryptionAtRest,
        boolean encryptionInTransit
) {
}
