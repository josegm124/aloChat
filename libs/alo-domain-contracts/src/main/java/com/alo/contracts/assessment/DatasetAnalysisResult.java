package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.List;

public record DatasetAnalysisResult(
        String assessmentId,
        String artifactId,
        PreferredLanguage preferredLanguage,
        Sector sector,
        String regulatoryProfileId,
        String format,
        long sizeBytes,
        int estimatedRowCount,
        int estimatedColumnCount,
        List<String> columnNames,
        List<String> observations,
        List<ControlFinding> findings,
        Instant analyzedAt
) {
}
