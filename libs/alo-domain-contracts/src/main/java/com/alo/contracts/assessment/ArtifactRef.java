package com.alo.contracts.assessment;

import java.time.Instant;
import java.util.Map;

public record ArtifactRef(
        String artifactId,
        ArtifactType artifactType,
        String fileName,
        String s3Bucket,
        String s3Key,
        String checksum,
        String contentType,
        long sizeBytes,
        Instant uploadedAt,
        Map<String, String> metadata
) {
}
