package com.alo.contracts.assessment;

public record DataSourceProfile(
        String dataType,
        String origin,
        boolean containsPersonalData,
        boolean containsSensitiveData,
        String representativeness,
        String dataGovernance,
        String preprocessingDescription
) {
}
