package com.alochat.contracts.message;

public record Attachment(
        String type,
        String url,
        String mimeType
) {
}
