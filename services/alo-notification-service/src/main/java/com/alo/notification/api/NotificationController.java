package com.alo.notification.api;

import com.alo.contracts.assessment.GeneratedNotification;
import com.alo.notification.service.NotificationGenerationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationGenerationService notificationGenerationService;

    public NotificationController(NotificationGenerationService notificationGenerationService) {
        this.notificationGenerationService = notificationGenerationService;
    }

    @PostMapping("/generate")
    public ResponseEntity<GeneratedNotification> generate(@Valid @RequestBody GenerateNotificationRequest request) {
        return ResponseEntity.ok(notificationGenerationService.generate(request));
    }
}
