package com.alochat.inbound.api.web;

import jakarta.validation.constraints.NotBlank;

public record WebUser(
        @NotBlank String id,
        @NotBlank String role,
        String name
) {
}
