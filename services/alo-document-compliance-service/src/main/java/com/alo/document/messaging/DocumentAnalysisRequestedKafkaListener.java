package com.alo.document.messaging;

import com.alo.contracts.assessment.DocumentAnalysisResult;
import com.alo.contracts.events.DocumentAnalysisRequestedEvent;
import com.alo.document.service.DocumentAnalysisCompletedEventPublisher;
import com.alo.document.service.DocumentComplianceAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnalysisRequestedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisRequestedKafkaListener.class);
    private final DocumentComplianceAnalysisService documentComplianceAnalysisService;
    private final DocumentAnalysisCompletedEventPublisher documentAnalysisCompletedEventPublisher;

    public DocumentAnalysisRequestedKafkaListener(
            DocumentComplianceAnalysisService documentComplianceAnalysisService,
            DocumentAnalysisCompletedEventPublisher documentAnalysisCompletedEventPublisher
    ) {
        this.documentComplianceAnalysisService = documentComplianceAnalysisService;
        this.documentAnalysisCompletedEventPublisher = documentAnalysisCompletedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-document-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDocumentAnalysisRequested(DocumentAnalysisRequestedEvent event) {
        log.info(
                "document analysis started assessmentId={} artifactId={} traceId={}",
                event.assessment().assessmentId(),
                event.artifact().artifactId(),
                event.traceId()
        );
        DocumentAnalysisResult result = documentComplianceAnalysisService.analyze(event);
        log.info(
                "document analysis completed assessmentId={} findings={} evidences={} extractedLength={}",
                event.assessment().assessmentId(),
                result.findings().size(),
                result.evidenceItems().size(),
                result.extractedCharacterCount()
        );
        documentAnalysisCompletedEventPublisher.publish(
                event.traceId(),
                event.assessment(),
                event.regulatoryProfile(),
                result
        );
        log.info(
                "document analysis completed event published assessmentId={} traceId={}",
                event.assessment().assessmentId(),
                event.traceId()
        );
    }
}
