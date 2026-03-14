package com.alochat.inbound.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebInboundRequest(
        @NotBlank String tenantId,
        @NotBlank String messageId,
        @NotBlank String conversationId,
        @NotNull @Valid WebUser user,
        @NotNull @Valid WebMessage message,
        @NotNull @Valid WebContext context,
        @Valid WebMetadata metadata
) {
}
