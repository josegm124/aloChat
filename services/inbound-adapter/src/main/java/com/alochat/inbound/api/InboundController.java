package com.alochat.inbound.api;

import com.alochat.contracts.message.Channel;
import com.alochat.inbound.api.web.WebInboundRequest;
import com.alochat.inbound.model.InboundRequestContext;
import com.alochat.inbound.service.InboundMessageOrchestrator;
import com.alochat.inbound.service.MessageIdentityService;
import com.alochat.inbound.service.WebRequestAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inbound")
public class InboundController {

    private final InboundMessageOrchestrator orchestrator;
    private final MessageIdentityService identityService;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final WebRequestAuthenticationService webRequestAuthenticationService;

    public InboundController(
            InboundMessageOrchestrator orchestrator,
            MessageIdentityService identityService,
            ObjectMapper objectMapper,
            Validator validator,
            WebRequestAuthenticationService webRequestAuthenticationService
    ) {
        this.orchestrator = orchestrator;
        this.identityService = identityService;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.webRequestAuthenticationService = webRequestAuthenticationService;
    }

    @PostMapping("/web")
    public ResponseEntity<AcceptedMessageResponse> acceptWeb(
            @RequestBody String rawBody,
            HttpServletRequest request
    ) throws IOException {
        Map<String, String> headers = extractHeaders(request);
        webRequestAuthenticationService.validate(rawBody, headers);

        WebInboundRequest payload = objectMapper.readValue(rawBody, WebInboundRequest.class);
        validateWebPayload(payload);
        return accept(Channel.WEB, objectMapper.valueToTree(payload), request, headers);
    }

    @PostMapping("/telegram")
    public ResponseEntity<AcceptedMessageResponse> acceptTelegram(
            @RequestBody JsonNode payload,
            HttpServletRequest request
    ) {
        return accept(Channel.TELEGRAM, payload, request, extractHeaders(request));
    }

    @PostMapping("/meta")
    public ResponseEntity<AcceptedMessageResponse> acceptMeta(
            @RequestBody JsonNode payload,
            HttpServletRequest request
    ) {
        return accept(Channel.META, payload, request, extractHeaders(request));
    }

    private ResponseEntity<AcceptedMessageResponse> accept(
            Channel channel,
            JsonNode payload,
            HttpServletRequest request,
            Map<String, String> headers
    ) {
        InboundRequestContext context = new InboundRequestContext(
                channel,
                Instant.now(),
                identityService.resolveTraceId(headers),
                request.getRemoteAddr(),
                identityService.resolveExternalRequestId(headers),
                headers
        );

        AcceptedMessageResponse response = orchestrator.handle(channel, payload, context);
        return ResponseEntity.accepted().body(response);
    }

    private void validateWebPayload(WebInboundRequest payload) {
        Set<ConstraintViolation<WebInboundRequest>> violations = validator.validate(payload);
        if (violations.isEmpty()) {
            return;
        }

        Set<String> messages = new LinkedHashSet<>();
        for (ConstraintViolation<WebInboundRequest> violation : violations) {
            messages.add(violation.getPropertyPath() + " " + violation.getMessage());
        }
        throw new IllegalArgumentException(String.join("; ", messages));
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String headerName : Collections.list(request.getHeaderNames())) {
            headers.put(headerName.toLowerCase(), request.getHeader(headerName));
        }
        return headers;
    }
}
