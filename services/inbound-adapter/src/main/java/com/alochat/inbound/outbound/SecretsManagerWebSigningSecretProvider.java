package com.alochat.inbound.outbound;

import com.alochat.inbound.security.WebSigningSecretProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

@Component
public class SecretsManagerWebSigningSecretProvider implements WebSigningSecretProvider {

    private final SecretsManagerClient secretsManagerClient;
    private final ObjectMapper objectMapper;
    private final String secretPrefix;
    private final String secretSuffix;

    public SecretsManagerWebSigningSecretProvider(
            SecretsManagerClient secretsManagerClient,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${alochat.web.secret-prefix}") String secretPrefix,
            @org.springframework.beans.factory.annotation.Value("${alochat.web.secret-suffix}") String secretSuffix
    ) {
        this.secretsManagerClient = secretsManagerClient;
        this.objectMapper = objectMapper;
        this.secretPrefix = stripTrailingSlash(secretPrefix);
        this.secretSuffix = stripLeadingSlash(secretSuffix);
    }

    @Override
    public String getSigningSecret(String tenantId, String channel) {
        try {
            String secretId = secretIdFor(tenantId, channel);
            String secretString = secretsManagerClient.getSecretValue(
                    GetSecretValueRequest.builder().secretId(secretId).build()
            ).secretString();
            JsonNode jsonNode = objectMapper.readTree(secretString);
            return jsonNode.path("hmacKey").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to resolve web signing secret from AWS Secrets Manager", exception);
        }
    }

    private String secretIdFor(String tenantId, String channel) {
        return secretPrefix + "/" + tenantId + "/channels/" + channel + "/" + secretSuffix;
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
