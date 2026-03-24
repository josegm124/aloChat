package com.alo.contracts.assessment;

public record MonitoringPolicy(
        String monitoringType,
        String frequency,
        String responsibleRole,
        boolean incidentLoggingEnabled
) {
}
