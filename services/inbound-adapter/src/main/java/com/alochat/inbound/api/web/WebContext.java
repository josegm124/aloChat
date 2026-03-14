package com.alochat.inbound.api.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record WebContext(
        @NotBlank String source,
        @NotBlank String locale,
        @NotNull @Valid WebClient client,
        @Valid WebSession session
) {
}
