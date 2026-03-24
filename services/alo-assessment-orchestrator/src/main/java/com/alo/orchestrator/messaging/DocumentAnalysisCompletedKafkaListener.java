package com.alo.orchestrator.messaging;

import com.alo.contracts.events.DocumentAnalysisCompletedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnalysisCompletedKafkaListener {
    private final AssessmentAnalysisCoordinator assessmentAnalysisCoordinator;

    public DocumentAnalysisCompletedKafkaListener(AssessmentAnalysisCoordinator assessmentAnalysisCoordinator) {
        this.assessmentAnalysisCoordinator = assessmentAnalysisCoordinator;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-document-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDocumentAnalysisCompleted(DocumentAnalysisCompletedEvent event) {
        assessmentAnalysisCoordinator.onDocumentCompleted(event);
    }
}
