package com.alochat.inbound.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

public final class JsonNodeReader {

    private JsonNodeReader() {
    }

    public static JsonNode firstArrayElement(JsonNode root, String fieldName) {
        JsonNode array = root.path(fieldName);
        if (!array.isArray() || array.size() == 0) {
            return MissingNode.getInstance();
        }
        return array.get(0);
    }

    public static String text(JsonNode root, String... fields) {
        JsonNode current = root;
        for (String field : fields) {
            current = current.path(field);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value;
    }

    public static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second;
    }

    public static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
