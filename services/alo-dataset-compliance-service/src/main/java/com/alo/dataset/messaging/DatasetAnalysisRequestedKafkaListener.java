package com.alo.dataset.messaging;

import com.alo.contracts.assessment.DatasetAnalysisResult;
import com.alo.contracts.events.DatasetAnalysisRequestedEvent;
import com.alo.dataset.service.DatasetAnalysisCompletedEventPublisher;
import com.alo.dataset.service.DatasetComplianceAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DatasetAnalysisRequestedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(DatasetAnalysisRequestedKafkaListener.class);
    private final DatasetComplianceAnalysisService datasetComplianceAnalysisService;
    private final DatasetAnalysisCompletedEventPublisher datasetAnalysisCompletedEventPublisher;

    public DatasetAnalysisRequestedKafkaListener(
            DatasetComplianceAnalysisService datasetComplianceAnalysisService,
            DatasetAnalysisCompletedEventPublisher datasetAnalysisCompletedEventPublisher
    ) {
        this.datasetComplianceAnalysisService = datasetComplianceAnalysisService;
        this.datasetAnalysisCompletedEventPublisher = datasetAnalysisCompletedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-dataset-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onDatasetAnalysisRequested(DatasetAnalysisRequestedEvent event) {
        log.info(
                "dataset analysis started assessmentId={} artifactId={} traceId={}",
                event.assessment().assessmentId(),
                event.artifact().artifactId(),
                event.traceId()
        );
        DatasetAnalysisResult result = datasetComplianceAnalysisService.analyze(event);
        log.info(
                "dataset analysis completed assessmentId={} findings={} observations={} detectedFormat={}",
                event.assessment().assessmentId(),
                result.findings().size(),
                result.observations().size(),
                result.format()
        );
        datasetAnalysisCompletedEventPublisher.publish(
                event.traceId(),
                event.assessment(),
                event.regulatoryProfile(),
                result
        );
        log.info(
                "dataset analysis completed event published assessmentId={} traceId={}",
                event.assessment().assessmentId(),
                event.traceId()
        );
    }
}
