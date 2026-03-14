package com.alochat.inbound.api.web;

import jakarta.validation.constraints.NotBlank;

public record WebSession(
        @NotBlank String id,
        String anonymousId
) {
}
