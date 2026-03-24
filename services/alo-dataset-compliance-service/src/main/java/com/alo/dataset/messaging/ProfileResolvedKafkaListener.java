package com.alo.dataset.messaging;

import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.ArtifactType;
import com.alo.contracts.events.ProfileResolvedEvent;
import com.alo.dataset.service.DatasetAnalysisRequestedEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileResolvedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(ProfileResolvedKafkaListener.class);
    private final DatasetAnalysisRequestedEventPublisher datasetAnalysisRequestedEventPublisher;

    public ProfileResolvedKafkaListener(
            DatasetAnalysisRequestedEventPublisher datasetAnalysisRequestedEventPublisher
    ) {
        this.datasetAnalysisRequestedEventPublisher = datasetAnalysisRequestedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-profile-resolved}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onProfileResolved(ProfileResolvedEvent event) {
        for (ArtifactRef artifact : event.assessment().artifacts()) {
            if (isDatasetArtifact(artifact.artifactType())) {
                log.info(
                        "dataset analysis requested assessmentId={} artifactId={} fileName={} traceId={}",
                        event.assessment().assessmentId(),
                        artifact.artifactId(),
                        artifact.fileName(),
                        event.traceId()
                );
                datasetAnalysisRequestedEventPublisher.publish(event, artifact);
                break;
            }
        }
    }

    private boolean isDatasetArtifact(ArtifactType artifactType) {
        return artifactType == ArtifactType.DATASET_CSV
                || artifactType == ArtifactType.DATASET_XLSX
                || artifactType == ArtifactType.DATASET_PARQUET;
    }
}
