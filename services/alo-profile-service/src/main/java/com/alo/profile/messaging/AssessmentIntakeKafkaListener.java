package com.alo.profile.messaging;

import com.alo.contracts.assessment.RegulatoryProfile;
import com.alo.contracts.events.AssessmentIntakeReceivedEvent;
import com.alo.profile.service.ProfileResolvedEventPublisher;
import com.alo.profile.service.RegulatoryProfileResolverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AssessmentIntakeKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(AssessmentIntakeKafkaListener.class);
    private final RegulatoryProfileResolverService regulatoryProfileResolverService;
    private final ProfileResolvedEventPublisher profileResolvedEventPublisher;

    public AssessmentIntakeKafkaListener(
            RegulatoryProfileResolverService regulatoryProfileResolverService,
            ProfileResolvedEventPublisher profileResolvedEventPublisher
    ) {
        this.regulatoryProfileResolverService = regulatoryProfileResolverService;
        this.profileResolvedEventPublisher = profileResolvedEventPublisher;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-intake-received}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onAssessmentIntakeReceived(AssessmentIntakeReceivedEvent event) {
        log.info(
                "profile resolution started assessmentId={} traceId={} sector={}",
                event.assessment().assessmentId(),
                event.traceId(),
                event.assessment().sector()
        );
        RegulatoryProfile regulatoryProfile = regulatoryProfileResolverService.resolve(event.assessment());
        log.info(
                "profile resolved assessmentId={} regulatoryProfileId={} highRiskLikely={} frameworks={}",
                event.assessment().assessmentId(),
                regulatoryProfile.regulatoryProfileId(),
                regulatoryProfile.highRiskLikely(),
                regulatoryProfile.applicableFrameworks().size()
        );
        profileResolvedEventPublisher.publish(event, regulatoryProfile);
        log.info(
                "profile resolved event published assessmentId={} traceId={}",
                event.assessment().assessmentId(),
                event.traceId()
        );
    }
}
