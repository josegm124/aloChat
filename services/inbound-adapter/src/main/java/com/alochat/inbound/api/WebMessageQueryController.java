package com.alochat.inbound.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import com.alochat.inbound.service.WebMessageStatusService;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
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

    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<MessageStatusResponse>> getConversationMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit
    ) {
        return ResponseEntity.ok(webMessageStatusService.findConversationMessages(conversationId, limit));
    }
}
