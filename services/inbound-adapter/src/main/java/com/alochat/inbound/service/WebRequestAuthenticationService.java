package com.alochat.inbound.service;

import com.alochat.inbound.security.InboundAuthenticationException;
import com.alochat.inbound.security.WebSigningSecretProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebRequestAuthenticationService {

    private final WebSigningSecretProvider webSigningSecretProvider;
    private final long maxClockSkewSeconds;

    public WebRequestAuthenticationService(
            WebSigningSecretProvider webSigningSecretProvider,
            @Value("${alochat.web.max-clock-skew-seconds}") long maxClockSkewSeconds
    ) {
        this.webSigningSecretProvider = webSigningSecretProvider;
        this.maxClockSkewSeconds = maxClockSkewSeconds;
    }

    public void validate(String rawBody, Map<String, String> headers) {
        String tenantId = readRequired(headers, "x-tenant-id");
        String timestamp = readRequired(headers, "x-timestamp");
        String signature = readRequired(headers, "x-signature");
        long requestEpoch = parseTimestamp(timestamp);
        long now = Instant.now().getEpochSecond();

        if (Math.abs(now - requestEpoch) > maxClockSkewSeconds) {
            throw new InboundAuthenticationException("Expired or invalid request timestamp");
        }

        String expectedSignature = sign(
                timestamp + "." + rawBody,
                webSigningSecretProvider.getSigningSecret(tenantId, "web")
        );
        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new InboundAuthenticationException("Invalid request signature");
        }
    }

    private long parseTimestamp(String timestamp) {
        try {
            return Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new InboundAuthenticationException("Invalid x-timestamp header");
        }
    }

    private String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute request signature", exception);
        }
    }

    private String readRequired(Map<String, String> headers, String key) {
        String value = headers.get(key);
        if (value == null || value.isBlank()) {
            throw new InboundAuthenticationException("Missing required header: " + key);
        }
        return value;
    }
}
