package com.alo.report.messaging;

import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.contracts.events.ReportGenerationRequestedEvent;
import com.alo.report.api.PublishedReportResponse;
import com.alo.report.service.AssessmentReportGenerationService;
import com.alo.report.service.NotificationGenerationRequestedEventPublisher;
import com.alo.report.storage.S3ReportStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ReportGenerationRequestedKafkaListener {
    private static final Logger log = LoggerFactory.getLogger(ReportGenerationRequestedKafkaListener.class);
    private final AssessmentReportGenerationService assessmentReportGenerationService;
    private final NotificationGenerationRequestedEventPublisher notificationGenerationRequestedEventPublisher;
    private final S3ReportStorageService s3ReportStorageService;

    public ReportGenerationRequestedKafkaListener(
            AssessmentReportGenerationService assessmentReportGenerationService,
            NotificationGenerationRequestedEventPublisher notificationGenerationRequestedEventPublisher,
            S3ReportStorageService s3ReportStorageService
    ) {
        this.assessmentReportGenerationService = assessmentReportGenerationService;
        this.notificationGenerationRequestedEventPublisher = notificationGenerationRequestedEventPublisher;
        this.s3ReportStorageService = s3ReportStorageService;
    }

    @KafkaListener(
            topics = "${alo.kafka.topics.assessment-report-requested}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onReportGenerationRequested(ReportGenerationRequestedEvent event) {
        log.info(
                "report generation started assessmentId={} traceId={}",
                event.consolidatedAssessmentResult().assessmentId(),
                event.traceId()
        );
        GeneratedAssessmentReport report =
                assessmentReportGenerationService.generate(event.consolidatedAssessmentResult());
        PublishedReportResponse publishedReport = s3ReportStorageService.store(report);
        log.info(
                "report generated assessmentId={} sections={} pdfBytes={} reportUrl={}",
                event.consolidatedAssessmentResult().assessmentId(),
                report.webReport().sections().size(),
                report.pdfReportArtifact().base64Content().length(),
                publishedReport.reportAccessUrl()
        );
        notificationGenerationRequestedEventPublisher.publish(
                event.traceId(),
                event.consolidatedAssessmentResult().tenantId(),
                publishedReport
        );
        log.info(
                "notification requested assessmentId={} traceId={}",
                event.consolidatedAssessmentResult().assessmentId(),
                event.traceId()
        );
    }
}
