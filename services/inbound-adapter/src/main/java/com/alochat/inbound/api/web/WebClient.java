package com.alochat.inbound.api.web;

import jakarta.validation.constraints.NotBlank;

public record WebClient(
        @NotBlank String app,
        @NotBlank String version,
        String platform
) {
}
