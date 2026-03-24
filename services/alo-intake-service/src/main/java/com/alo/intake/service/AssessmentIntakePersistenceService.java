package com.alo.intake.service;

import com.alo.contracts.assessment.AssessmentEnvelope;
import com.alo.intake.api.CreateAssessmentRequest;
import com.alo.intake.persistence.AssessmentItem;
import com.alo.intake.persistence.AssessmentRecordRepository;
import com.alo.intake.persistence.IdempotencyItem;
import com.alo.intake.persistence.IdempotencyRecordRepository;
import com.alo.intake.persistence.SubmissionItem;
import com.alo.intake.persistence.SubmissionRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AssessmentIntakePersistenceService {
    private final ObjectMapper objectMapper;
    private final AssessmentRecordRepository assessmentRecordRepository;
    private final SubmissionRecordRepository submissionRecordRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public AssessmentIntakePersistenceService(
            ObjectMapper objectMapper,
            AssessmentRecordRepository assessmentRecordRepository,
            SubmissionRecordRepository submissionRecordRepository,
            IdempotencyRecordRepository idempotencyRecordRepository
    ) {
        this.objectMapper = objectMapper;
        this.assessmentRecordRepository = assessmentRecordRepository;
        this.submissionRecordRepository = submissionRecordRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    public Optional<AssessmentEnvelope> findExistingAssessment(String idempotencyKey) {
        return idempotencyRecordRepository.findByKey(idempotencyKey)
                .flatMap(item -> assessmentRecordRepository.findById(item.getAssessmentId()))
                .map(this::toAssessmentEnvelope);
    }

    public boolean reserveIdempotency(AssessmentEnvelope assessment, Instant createdAt) {
        IdempotencyItem item = new IdempotencyItem();
        item.setIdempotencyKey(assessment.idempotencyKey());
        item.setAssessmentId(assessment.assessmentId());
        item.setSubmissionId(assessment.submissionId());
        item.setTraceId(assessment.traceId());
        item.setStatus(assessment.status().name());
        item.setCreatedAt(createdAt.toString());
        return idempotencyRecordRepository.tryCreate(item);
    }

    public void persist(CreateAssessmentRequest request, AssessmentEnvelope assessment) {
        assessmentRecordRepository.save(toAssessmentItem(assessment));
        submissionRecordRepository.save(toSubmissionItem(request, assessment));
    }

    private AssessmentItem toAssessmentItem(AssessmentEnvelope assessment) {
        AssessmentItem item = new AssessmentItem();
        item.setAssessmentId(assessment.assessmentId());
        item.setSubmissionId(assessment.submissionId());
        item.setTenantId(assessment.tenantId());
        item.setOrganizationId(assessment.organizationId());
        item.setUserId(assessment.userId());
        item.setIdempotencyKey(assessment.idempotencyKey());
        item.setStatus(assessment.status().name());
        item.setCreatedAt(assessment.createdAt().toString());
        item.setUpdatedAt(assessment.updatedAt().toString());
        item.setAssessmentJson(writeJson(assessment));
        return item;
    }

    private SubmissionItem toSubmissionItem(CreateAssessmentRequest request, AssessmentEnvelope assessment) {
        SubmissionItem item = new SubmissionItem();
        item.setSubmissionId(assessment.submissionId());
        item.setAssessmentId(assessment.assessmentId());
        item.setTenantId(assessment.tenantId());
        item.setOrganizationId(assessment.organizationId());
        item.setUserId(assessment.userId());
        item.setCreatedAt(assessment.createdAt().toString());
        item.setRequestJson(writeJson(request));
        return item;
    }

    private AssessmentEnvelope toAssessmentEnvelope(AssessmentItem item) {
        try {
            return objectMapper.readValue(item.getAssessmentJson(), AssessmentEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize persisted assessment envelope", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize value for persistence", exception);
        }
    }
}
