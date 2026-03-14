package com.alochat.inbound.validation;

import com.fasterxml.jackson.databind.JsonNode;

public abstract class JsonPayloadValidatorSupport {

    protected void requireText(JsonNode payload, String... path) {
        JsonNode current = payload;
        String joinedPath = String.join(".", path);
        for (String segment : path) {
            current = current.path(segment);
        }
        if (current.isMissingNode() || current.isNull() || current.asText().isBlank()) {
            throw new InboundValidationException("Missing required field: " + joinedPath);
        }
    }

    protected JsonNode requireFirstArrayElement(JsonNode payload, String fieldName) {
        JsonNode array = payload.path(fieldName);
        if (!array.isArray() || array.isEmpty()) {
            throw new InboundValidationException("Missing required array element: " + fieldName + "[0]");
        }
        return array.get(0);
    }
}
