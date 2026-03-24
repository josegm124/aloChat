package com.alo.notification.service;

import com.alo.contracts.assessment.GeneratedAssessmentReport;
import com.alo.contracts.assessment.GeneratedNotification;
import com.alo.contracts.assessment.PreferredLanguage;
import com.alo.contracts.events.NotificationGenerationRequestedEvent;
import com.alo.notification.api.GenerateNotificationRequest;
import com.alo.notification.delivery.SesEmailDeliveryService;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationGenerationService {
    private final SesEmailDeliveryService sesEmailDeliveryService;

    public NotificationGenerationService(SesEmailDeliveryService sesEmailDeliveryService) {
        this.sesEmailDeliveryService = sesEmailDeliveryService;
    }

    public GeneratedNotification generate(GenerateNotificationRequest request) {
        GeneratedAssessmentReport report = request.generatedAssessmentReport();
        PreferredLanguage preferredLanguage = report.preferredLanguage();
        String subject = subject(preferredLanguage);
        String body = body(preferredLanguage, report.assessmentId(), request.reportAccessUrl());
        SesEmailDeliveryService.DeliveryResult deliveryResult =
                sesEmailDeliveryService.send(request.recipients(), subject, body);

        return new GeneratedNotification(
                UUID.randomUUID().toString(),
                report.assessmentId(),
                request.tenantId(),
                preferredLanguage,
                "EMAIL",
                List.copyOf(request.recipients()),
                subject,
                body,
                request.reportAccessUrl(),
                deliveryResult.status(),
                deliveryResult.providerMessageId(),
                Instant.now()
        );
    }

    public GeneratedNotification generate(NotificationGenerationRequestedEvent event) {
        GeneratedAssessmentReport report = event.generatedAssessmentReport();
        PreferredLanguage preferredLanguage = report.preferredLanguage();
        String subject = subject(preferredLanguage);
        String body = body(preferredLanguage, report.assessmentId(), event.reportAccessUrl());
        SesEmailDeliveryService.DeliveryResult deliveryResult =
                sesEmailDeliveryService.send(event.recipients(), subject, body);

        return new GeneratedNotification(
                UUID.randomUUID().toString(),
                report.assessmentId(),
                event.tenantId(),
                preferredLanguage,
                "EMAIL",
                List.copyOf(event.recipients()),
                subject,
                body,
                event.reportAccessUrl(),
                deliveryResult.status(),
                deliveryResult.providerMessageId(),
                Instant.now()
        );
    }

    private String subject(PreferredLanguage preferredLanguage) {
        return isSpanish(preferredLanguage)
                ? "Tu reporte de cumplimiento ya esta listo"
                : "Your compliance report is ready";
    }

    private String body(PreferredLanguage preferredLanguage, String assessmentId, String reportAccessUrl) {
        if (isSpanish(preferredLanguage)) {
            return """
                    Hola,

                    El reporte de cumplimiento para el assessment %s ya esta disponible.
                    Puedes revisarlo en el siguiente enlace:
                    %s

                    Este enlace debe resolverse despues con CloudFront o el frontend autenticado.
                    """
                    .formatted(assessmentId, reportAccessUrl)
                    .trim();
        }
        return """
                Hello,

                The compliance report for assessment %s is now available.
                You can review it using the following link:
                %s

                This link is expected to be backed later by CloudFront or the authenticated frontend.
                """
                .formatted(assessmentId, reportAccessUrl)
                .trim();
    }

    private boolean isSpanish(PreferredLanguage preferredLanguage) {
        return preferredLanguage == PreferredLanguage.ES;
    }
}
