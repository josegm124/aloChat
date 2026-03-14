package com.alochat.inbound.api.web;

import jakarta.validation.constraints.NotBlank;

public record WebMessage(
        @NotBlank String type,
        @NotBlank String text
) {
}
