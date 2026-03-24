package com.alo.intake.api;

import com.alo.contracts.assessment.ArtifactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

public record CreateArtifactRequest(
        @NotNull ArtifactType artifactType,
        @NotBlank String fileName,
        @NotBlank String s3Bucket,
        @NotBlank String s3Key,
        String checksum,
        @NotBlank String contentType,
        long sizeBytes,
        @NotNull Instant uploadedAt,
        Map<String, String> metadata
) {
}
