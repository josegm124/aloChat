package com.alo.intake.service;

import com.alo.contracts.events.AssessmentIntakeReceivedEvent;
import com.alo.contracts.events.KafkaTopics;
import com.alo.support.kafka.ResilientKafkaPublisher;
import org.springframework.stereotype.Component;

@Component
public class AssessmentIntakeEventPublisher {
    private final ResilientKafkaPublisher resilientKafkaPublisher;

    public AssessmentIntakeEventPublisher(ResilientKafkaPublisher resilientKafkaPublisher) {
        this.resilientKafkaPublisher = resilientKafkaPublisher;
    }

    public void publish(AssessmentIntakeReceivedEvent event) {
        resilientKafkaPublisher.send(KafkaTopics.ASSESSMENT_INTAKE_RECEIVED, event.assessment().assessmentId(), event);
    }
}
