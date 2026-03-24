package com.alo.contracts.assessment;

import java.util.List;

public record ModelTraceability(
        String modelName,
        String modelType,
        String providerType,
        String trainingOrigin,
        String version,
        boolean certified,
        List<String> certifications,
        String documentationUrl
) {
}
