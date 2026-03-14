package com.alochat.inbound.security;

public interface WebSigningSecretProvider {

    String getSigningSecret(String tenantId, String channel);
}
