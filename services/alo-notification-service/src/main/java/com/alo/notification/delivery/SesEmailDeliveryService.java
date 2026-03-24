package com.alo.notification.delivery;

import com.alo.notification.config.SesProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;

import java.util.List;

@Service
public class SesEmailDeliveryService {
    private final SesV2Client sesV2Client;
    private final SesProperties sesProperties;

    public SesEmailDeliveryService(
            SesV2Client sesV2Client,
            SesProperties sesProperties
    ) {
        this.sesV2Client = sesV2Client;
        this.sesProperties = sesProperties;
    }

    public DeliveryResult send(
            List<String> recipients,
            String subject,
            String body
    ) {
        if (recipients == null || recipients.isEmpty() || recipients.stream().allMatch(this::isPlaceholderRecipient)) {
            return new DeliveryResult("SKIPPED", null);
        }
        if (sesProperties.fromAddress() == null || sesProperties.fromAddress().isBlank()) {
            return new DeliveryResult("SKIPPED", null);
        }

        SendEmailResponse response = sesV2Client.sendEmail(
                SendEmailRequest.builder()
                        .fromEmailAddress(sesProperties.fromAddress())
                        .destination(Destination.builder().toAddresses(recipients).build())
                        .content(EmailContent.builder()
                                .simple(Message.builder()
                                        .subject(Content.builder().data(subject).build())
                                        .body(Body.builder().text(Content.builder().data(body).build()).build())
                                        .build())
                                .build())
                        .build()
        );
        return new DeliveryResult("SENT", response.messageId());
    }

    private boolean isPlaceholderRecipient(String recipient) {
        return recipient == null || recipient.endsWith("@alo.local");
    }

    public record DeliveryResult(
            String status,
            String providerMessageId
    ) {
    }
}
