package com.alo.contracts.assessment;

public record PdfReportArtifact(
        String fileName,
        String contentType,
        long sizeBytes,
        String base64Content
) {
}
