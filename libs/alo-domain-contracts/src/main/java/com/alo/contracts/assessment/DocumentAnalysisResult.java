package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;

public record DocumentAnalysisResult(
        String assessmentId,
        String artifactId,
        PreferredLanguage preferredLanguage,
        Sector sector,
        String regulatoryProfileId,
        int pageCount,
        int extractedCharacterCount,
        String extractedTextPreview,
        List<EvidenceItem> evidenceItems,
        List<ControlFinding> findings,
        Instant analyzedAt
) {
}
