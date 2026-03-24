package com.alo.orchestrator.messaging;

import com.alo.contracts.events.DatasetAnalysisCompletedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DatasetAnalysisCompletedKafkaListener {
    private final AssessmentAnalysisCoordinator assessmentAnalysisCoordinator;

    public DatasetAnalysisCompletedKafkaListener(AssessmentAnalysisCoordinator assessmentAnalysisCoordinator) {
        this.assessmentAnalysisCoordinator = assessmentAnalysisCoordinator;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-dataset-completed}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDatasetAnalysisCompleted(DatasetAnalysisCompletedEvent event) {
        assessmentAnalysisCoordinator.onDatasetCompleted(event);
    }
}
