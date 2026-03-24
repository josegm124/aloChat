package com.alo.document.messaging;

import com.alo.contracts.assessment.ArtifactRef;
import com.alo.contracts.assessment.ArtifactType;
import com.alo.contracts.events.ProfileResolvedEvent;
import com.alo.document.service.DocumentAnalysisRequestedEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProfileResolvedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(ProfileResolvedKafkaListener.class);
    private final DocumentAnalysisRequestedEventPublisher documentAnalysisRequestedEventPublisher;

    public ProfileResolvedKafkaListener(
            DocumentAnalysisRequestedEventPublisher documentAnalysisRequestedEventPublisher
    ) {
        this.documentAnalysisRequestedEventPublisher = documentAnalysisRequestedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-profile-resolved}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onProfileResolved(ProfileResolvedEvent event) {
        for (ArtifactRef artifact : event.assessment().artifacts()) {
            if (artifact.artifactType() == ArtifactType.DOCUMENT_PDF) {
                log.info(
                        "document analysis requested assessmentId={} artifactId={} fileName={} traceId={}",
                        event.assessment().assessmentId(),
                        artifact.artifactId(),
                        artifact.fileName(),
                        event.traceId()
                );
                documentAnalysisRequestedEventPublisher.publish(event, artifact);
                break;
            }
        }
    }
}
