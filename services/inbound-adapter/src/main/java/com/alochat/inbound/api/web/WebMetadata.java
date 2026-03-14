package com.alochat.inbound.api.web;

public record WebMetadata(
        String correlationId,
        String page,
        String referrer
) {
}
