package com.alochat.ai.outbound.opensearch;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.regions.Region;

public class OpenSearchClient {
    private static final String SERVICE_NAME = "aoss";
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String EMPTY_BODY_HASH = sha256Hex("");
    private static final DateTimeFormatter AMZ_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT).withZone(ZoneOffset.UTC);

    private final AwsCredentialsProvider credentialsProvider;
    private final Region region;
    private final URI endpoint;
    private final HttpClient httpClient;
    private final Clock clock;

    public OpenSearchClient(URI endpoint, Region region, AwsCredentialsProvider credentialsProvider) {
        this(endpoint, region, credentialsProvider, HttpClient.newHttpClient(), Clock.systemUTC());
    }

    OpenSearchClient(
            URI endpoint,
            Region region,
            AwsCredentialsProvider credentialsProvider,
            HttpClient httpClient,
            Clock clock
    ) {
        this.credentialsProvider = credentialsProvider;
        this.region = region;
        this.endpoint = endpoint;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    public OpenSearchResponse execute(String method, String path, String body) {
        try {
            String requestBody = body == null ? "" : body;
            AwsCredentials credentials = credentialsProvider.resolveCredentials();
            Instant now = clock.instant();
            String amzDate = AMZ_DATE_FORMAT.format(now);
            String dateStamp = DATE_STAMP_FORMAT.format(now);
            String payloadHash = sha256Hex(requestBody);
            String normalizedPath = normalizePath(path);
            String host = endpoint.getHost();

            String canonicalHeaders = canonicalHeaders(host, amzDate, payloadHash, credentials);
            String signedHeaders = signedHeaders(credentials);
            String canonicalRequest = method.toUpperCase(Locale.ROOT)
                    + "\n" + normalizedPath
                    + "\n\n"
                    + canonicalHeaders
                    + "\n" + signedHeaders
                    + "\n" + payloadHash;

            String credentialScope = dateStamp + "/" + region.id() + "/" + SERVICE_NAME + "/aws4_request";
            String stringToSign = "AWS4-HMAC-SHA256\n"
                    + amzDate + "\n"
                    + credentialScope + "\n"
                    + sha256Hex(canonicalRequest);

            String signature = HexFormat.of().formatHex(signingKey(credentials.secretAccessKey(), dateStamp, region.id(), SERVICE_NAME)
                    .doFinal(stringToSign.getBytes(StandardCharsets.UTF_8)));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint.toString() + normalizedPath))
                    .header("Content-Type", "application/json")
                    .header("X-Amz-Date", amzDate)
                    .header("X-Amz-Content-Sha256", payloadHash)
                    .header("Authorization", authorizationHeader(credentials.accessKeyId(), credentialScope, signedHeaders, signature));

            if (credentials instanceof AwsSessionCredentials sessionCredentials) {
                builder.header("X-Amz-Security-Token", sessionCredentials.sessionToken());
            }

            if ("HEAD".equalsIgnoreCase(method)) {
                builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else {
                builder.method(method.toUpperCase(Locale.ROOT), HttpRequest.BodyPublishers.ofString(requestBody));
            }

            HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            String responseBody;
            try (InputStream stream = response.body()) {
                responseBody = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            return new OpenSearchResponse(response.statusCode(), responseBody);
        } catch (Exception exception) {
            throw new IllegalStateException("OpenSearch request failed", exception);
        }
    }

    public OpenSearchResponse head(String path) {
        return execute("HEAD", path, null);
    }

    public OpenSearchResponse put(String path, String body) {
        return execute("PUT", path, body);
    }

    public OpenSearchResponse post(String path, String body) {
        return execute("POST", path, body);
    }

    private String canonicalHeaders(String host, String amzDate, String payloadHash, AwsCredentials credentials) {
        StringBuilder builder = new StringBuilder()
                .append("content-type:application/json\n")
                .append("host:").append(host).append('\n')
                .append("x-amz-content-sha256:").append(payloadHash).append('\n')
                .append("x-amz-date:").append(amzDate).append('\n');

        if (credentials instanceof AwsSessionCredentials sessionCredentials) {
            builder.append("x-amz-security-token:").append(sessionCredentials.sessionToken()).append('\n');
        }
        return builder.toString();
    }

    private String signedHeaders(AwsCredentials credentials) {
        if (credentials instanceof AwsSessionCredentials) {
            return "content-type;host;x-amz-content-sha256;x-amz-date;x-amz-security-token";
        }
        return "content-type;host;x-amz-content-sha256;x-amz-date";
    }

    private String authorizationHeader(String accessKeyId, String credentialScope, String signedHeaders, String signature) {
        return "AWS4-HMAC-SHA256 Credential=" + accessKeyId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;
    }

    private static Mac signingKey(String secretKey, String dateStamp, String region, String service) throws Exception {
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        byte[] kSigning = hmac(kService, "aws4_request");
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(kSigning, HMAC_SHA256));
        return mac;
    }

    private static byte[] hmac(byte[] key, String message) throws Exception {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(key, HMAC_SHA256));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to compute SHA-256 hash", exception);
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    public record OpenSearchResponse(int statusCode, String body) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        public boolean isNotFound() {
            return statusCode == 404;
        }
    }
}
