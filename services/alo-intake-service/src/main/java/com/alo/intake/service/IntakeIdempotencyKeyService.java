package com.alo.intake.service;

import com.alo.intake.api.CreateAssessmentRequest;
import java.util.Comparator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class IntakeIdempotencyKeyService {

    public String generate(CreateAssessmentRequest request) {
        String fingerprint = String.join("|",
                request.tenantId(),
                request.organizationId(),
                request.userId(),
                request.sector().name(),
                request.useCaseType(),
                request.aiSystemCategory(),
                request.geography(),
                request.systemName(),
                request.systemVersion(),
                request.provider(),
                request.deploymentContext(),
                Boolean.toString(request.datasetProvided()),
                artifactFingerprint(request)
        );
        return "alo:intake:" + sha256Hex(fingerprint);
    }

    private String artifactFingerprint(CreateAssessmentRequest request) {
        return request.artifacts().stream()
                .sorted(Comparator
                        .comparing((com.alo.intake.api.CreateArtifactRequest artifact) -> artifact.artifactType().name())
                        .thenComparing(com.alo.intake.api.CreateArtifactRequest::s3Bucket)
                        .thenComparing(com.alo.intake.api.CreateArtifactRequest::s3Key))
                .map(artifact -> String.join(":",
                        artifact.artifactType().name(),
                        artifact.s3Bucket(),
                        artifact.s3Key(),
                        artifact.checksum() == null ? "" : artifact.checksum(),
                        Long.toString(artifact.sizeBytes())))
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to generate idempotency key", exception);
        }
    }
}
