package com.alo.intake.service;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.contracts.assessment.AssessmentStatus;
import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.ArtifactType;
import com.alo.contracts.events.AssessmentIntakeReceivedEvent;
import com.alo.intake.api.CreateAssessmentAcceptedResponse;
import com.alo.intake.api.CreateAssessmentRequest;
import com.alo.intake.api.CreateArtifactRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class AssessmentIntakeService {
    private static final Logger log = LoggerFactory.getLogger(AssessmentIntakeService.class);
    private final Clock clock = Clock.systemUTC();
    private final IntakeIdempotencyKeyService intakeIdempotencyKeyService;
    private final AssessmentIntakePersistenceService assessmentIntakePersistenceService;
    private final AssessmentIntakeEventPublisher assessmentIntakeEventPublisher;

    @Autowired
    public AssessmentIntakeService(
            IntakeIdempotencyKeyService intakeIdempotencyKeyService,
            AssessmentIntakePersistenceService assessmentIntakePersistenceService,
            AssessmentIntakeEventPublisher assessmentIntakeEventPublisher
    ) {
        this.intakeIdempotencyKeyService = intakeIdempotencyKeyService;
        this.assessmentIntakePersistenceService = assessmentIntakePersistenceService;
        this.assessmentIntakeEventPublisher = assessmentIntakeEventPublisher;
    }

    public CreateAssessmentAcceptedResponse createAssessment(CreateAssessmentRequest request) {
        Instant now = clock.instant();
        String idempotencyKey = intakeIdempotencyKeyService.generate(request);
        log.info(
                "assessment intake received tenantId={} organizationId={} userId={} sector={} datasetProvided={} artifacts={}",
                request.tenantId(),
                request.organizationId(),
                request.userId(),
                request.sector(),
                request.datasetProvided(),
                request.artifacts().size()
        );
        return assessmentIntakePersistenceService.findExistingAssessment(idempotencyKey)
                .map(existingAssessment -> {
                    log.info(
                            "assessment intake idempotent hit assessmentId={} submissionId={} traceId={}",
                            existingAssessment.assessmentId(),
                            existingAssessment.submissionId(),
                            existingAssessment.traceId()
                    );
                    return toAcceptedResponse(existingAssessment);
                })
                .orElseGet(() -> createNewAssessment(request, now, idempotencyKey));
    }

    private CreateAssessmentAcceptedResponse createNewAssessment(
            CreateAssessmentRequest request,
            Instant now,
            String idempotencyKey
    ) {
        List<ArtifactRef> artifacts = buildArtifacts(request);
        String assessmentId = UUID.randomUUID().toString();
        String submissionId = UUID.randomUUID().toString();
        String traceId = UUID.randomUUID().toString();
        String regulatoryProfileId = buildRegulatoryProfileId(request);

        AssessmentEnvelope assessment = new AssessmentEnvelope(
                assessmentId,
                submissionId,
                request.tenantId(),
                request.organizationId(),
                request.userId(),
                traceId,
                idempotencyKey,
                request.preferredLanguage(),
                request.sector(),
                request.useCaseType(),
                request.aiSystemCategory(),
                request.geography(),
                regulatoryProfileId,
                request.datasetProvided(),
                artifacts,
                AssessmentStatus.RECEIVED,
                now,
                now,
                metadataFrom(request)
        );

        log.info(
                "assessment envelope created assessmentId={} submissionId={} traceId={} regulatoryProfileId={}",
                assessmentId,
                submissionId,
                traceId,
                regulatoryProfileId
        );

        boolean reserved = assessmentIntakePersistenceService.reserveIdempotency(assessment, now);
        if (!reserved) {
            log.info("assessment intake idempotency race detected idempotencyKey={}", idempotencyKey);
            return assessmentIntakePersistenceService.findExistingAssessment(idempotencyKey)
                    .map(existingAssessment -> {
                        log.info(
                                "assessment intake resolved after idempotency race assessmentId={} submissionId={} traceId={}",
                                existingAssessment.assessmentId(),
                                existingAssessment.submissionId(),
                                existingAssessment.traceId()
                        );
                        return toAcceptedResponse(existingAssessment);
                    })
                    .orElseThrow(() -> new IllegalStateException(
                            "Idempotency record exists but persisted assessment could not be loaded"
                    ));
        }

        assessmentIntakePersistenceService.persist(request, assessment);
        log.info(
                "assessment persisted assessmentId={} submissionId={} artifactCount={}",
                assessment.assessmentId(),
                assessment.submissionId(),
                assessment.artifacts().size()
        );

        AssessmentIntakeReceivedEvent event = new AssessmentIntakeReceivedEvent(
                UUID.randomUUID().toString(),
                traceId,
                assessment,
                now
        );
        assessmentIntakeEventPublisher.publish(event);
        log.info(
                "assessment intake event published eventId={} assessmentId={} traceId={}",
                event.eventId(),
                assessment.assessmentId(),
                traceId
        );

        return toAcceptedResponse(assessment);
    }

    private String buildRegulatoryProfileId(CreateAssessmentRequest request) {
        return request.sector().name().toLowerCase()
                + ":"
                + normalize(request.geography())
                + ":"
                + normalize(request.aiSystemCategory());
    }

    private Map<String, String> metadataFrom(CreateAssessmentRequest request) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("systemName", request.systemName());
        metadata.put("systemVersion", request.systemVersion());
        metadata.put("provider", request.provider());
        metadata.put("deploymentContext", request.deploymentContext());
        metadata.put("artifactCount", Integer.toString(request.artifacts().size()));
        metadata.put("healthcareProfileIncluded", Boolean.toString(request.healthcareAuditProfile() != null));
        if (request.intakeAnswers() != null) {
            request.intakeAnswers().forEach((key, value) -> {
                if (key != null && value != null) {
                    metadata.put("intake." + key, value);
                }
            });
        }
        return metadata;
    }

    private String normalize(String value) {
        return value.trim().toLowerCase().replace(' ', '-');
    }

    private List<ArtifactRef> buildArtifacts(CreateAssessmentRequest request) {
        if (request.artifacts().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one PDF document artifact is required.");
        }

        boolean hasDocument = false;
        boolean hasDataset = false;
        for (CreateArtifactRequest artifact : request.artifacts()) {
            if (artifact.artifactType() == ArtifactType.DOCUMENT_PDF) {
                hasDocument = true;
            }
            if (isDatasetArtifact(artifact.artifactType())) {
                hasDataset = true;
            }
        }

        if (!hasDocument) {
            throw new ResponseStatusException(BAD_REQUEST, "A DOCUMENT_PDF artifact is required.");
        }
        if (request.datasetProvided() && !hasDataset) {
            throw new ResponseStatusException(BAD_REQUEST, "datasetProvided=true requires a dataset artifact.");
        }
        if (!request.datasetProvided() && hasDataset) {
            throw new ResponseStatusException(BAD_REQUEST, "Dataset artifacts require datasetProvided=true.");
        }

        return request.artifacts().stream()
                .map(this::toArtifactRef)
                .toList();
    }

    private ArtifactRef toArtifactRef(CreateArtifactRequest artifact) {
        return new ArtifactRef(
                UUID.randomUUID().toString(),
                artifact.artifactType(),
                artifact.fileName(),
                artifact.s3Bucket(),
                artifact.s3Key(),
                artifact.checksum(),
                artifact.contentType(),
                artifact.sizeBytes(),
                artifact.uploadedAt(),
                artifact.metadata() == null ? Map.of() : Map.copyOf(artifact.metadata())
        );
    }

    private boolean isDatasetArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.DATASET_CSV
                || artifactType == ArtifactType.DATASET_XLSX
                || artifactType == ArtifactType.DATASET_PARQUET;
    }

    private CreateAssessmentAcceptedResponse toAcceptedResponse(AssessmentEnvelope assessment) {
        return new CreateAssessmentAcceptedResponse(
                assessment.assessmentId(),
                assessment.submissionId(),
                assessment.traceId(),
                assessment.idempotencyKey(),
                assessment.regulatoryProfileId(),
                assessment.status().name(),
                assessment
        );
    }
}
