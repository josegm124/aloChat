package com.alo.contracts.events;

public final class KafkaTopics {
    public static final String ASSESSMENT_INTAKE_RECEIVED = "alo.assessment.intake.received";
    public static final String ASSESSMENT_PROFILE_RESOLVED = "alo.assessment.profile.resolved";
    public static final String ASSESSMENT_DOCUMENT_REQUESTED = "alo.assessment.document.requested";
    public static final String ASSESSMENT_DATASET_REQUESTED = "alo.assessment.dataset.requested";
    public static final String ASSESSMENT_DOCUMENT_COMPLETED = "alo.assessment.document.completed";
    public static final String ASSESSMENT_DATASET_COMPLETED = "alo.assessment.dataset.completed";
    public static final String ASSESSMENT_REPORT_REQUESTED = "alo.assessment.report.requested";
    public static final String ASSESSMENT_NOTIFICATION_REQUESTED = "alo.assessment.notification.requested";
    public static final String ASSESSMENT_NOTIFICATION_COMPLETED = "alo.assessment.notification.completed";
    public static final String RETRY_SHORT = "alo.retry.short";
    public static final String RETRY_LONG = "alo.retry.long";
    public static final String DLQ = "alo.dlq";

    private KafkaTopics() {
    }
}
