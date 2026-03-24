package com.alo.contracts.assessment;

import java.util.Map;

public record EvidenceItem(
        String evidenceId,
        String type,
        String location,
        String hash,
        String extractedText,
        Map<String, String> metadata
) {
}
