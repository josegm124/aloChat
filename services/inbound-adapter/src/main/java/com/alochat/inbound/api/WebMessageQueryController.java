package com.alochat.inbound.api;

import com.alochat.inbound.service.WebMessageStatusService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/web/messages")
public class WebMessageQueryController {

    private final WebMessageStatusService webMessageStatusService;

    public WebMessageQueryController(WebMessageStatusService webMessageStatusService) {
        this.webMessageStatusService = webMessageStatusService;
    }

    @GetMapping("/{messageId}")
    public ResponseEntity<MessageStatusResponse> getStatus(@PathVariable String messageId) {
        return ResponseEntity.ok(webMessageStatusService.findByMessageId(messageId));
    }
}
