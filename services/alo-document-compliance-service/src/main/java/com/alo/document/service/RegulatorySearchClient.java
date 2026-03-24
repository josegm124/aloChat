package com.alo.document.service;

import com.alo.contracts.assessment.ApplicableFramework;
import com.alo.contracts.assessment.CompliancePillar;
import com.alo.document.config.SearchProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Component
public class RegulatorySearchClient {
    private static final Logger log = LoggerFactory.getLogger(RegulatorySearchClient.class);
    private static final Aws4Signer SIGNER = Aws4Signer.create();

    private final SearchProperties properties;
    private final ObjectMapper objectMapper;
    private final DefaultCredentialsProvider credentialsProvider;
    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final List<FallbackDocument> fallbackCorpus;

    public RegulatorySearchClient(SearchProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.credentialsProvider = DefaultCredentialsProvider.create();
        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .region(Region.of(properties.bedrockRegion()))
                .credentialsProvider(credentialsProvider)
                .build();
        this.fallbackCorpus = loadFallbackCorpus();
    }

    public List<RegulatoryMatch> search(String queryText, String sector, int limit) {
        if (!properties.enabled()) {
            return fallbackSearch(queryText, sector, limit);
        }

        try {
            if (properties.endpoint() == null || properties.endpoint().isBlank()) {
                return fallbackSearch(queryText, sector, limit);
            }

            List<RegulatoryMatch> lexicalMatches = lexicalSearch(queryText, sector, Math.max(limit, properties.lexicalCandidates()));
            if (!properties.embeddingsEnabled()) {
                return lexicalMatches.stream().limit(limit).toList();
            }

            List<Double> queryEmbedding = embed(queryText);
            List<RegulatoryMatch> vectorMatches = vectorSearch(queryEmbedding, sector, Math.max(limit, properties.vectorCandidates()));
            return mergeMatches(lexicalMatches, vectorMatches, limit);
        } catch (RuntimeException exception) {
            log.warn("search retrieval failed, falling back to bundled corpus: {}", exception.getMessage());
            return fallbackSearch(queryText, sector, limit);
        }
    }

    public String buildQueryText(String... inputs) {
        return List.of(inputs).stream()
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> List.of(value.replace('\n', ' ').split("\\s+")).stream())
                .map(token -> token.replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}-]", "").toLowerCase(Locale.ROOT))
                .filter(token -> token.length() > 3)
                .distinct()
                .limit(120)
                .collect(Collectors.joining(" "));
    }

    private List<RegulatoryMatch> lexicalSearch(String queryText, String sector, int limit) {
        return executeSearch(buildLexicalSearchQuery(queryText, sector, limit), sector, false);
    }

    private List<RegulatoryMatch> vectorSearch(List<Double> queryEmbedding, String sector, int limit) {
        return executeSearch(buildVectorSearchQuery(queryEmbedding, limit), sector, true);
    }

    private List<RegulatoryMatch> executeSearch(Map<String, Object> body, String sector, boolean vectorSearch) {
        try {
            String payload = objectMapper.writeValueAsString(body);
            URI uri = URI.create(properties.endpoint().replaceAll("/+$", "") + "/" + properties.corpusIndex() + "/_search");
            SignedResponse response = signedRequest(uri, payload);
            if (response.statusCode() != 200) {
                throw new IllegalStateException("OpenSearch query failed with status " + response.statusCode() + ": " + response.body());
            }
            return parseMatches(response.body(), sector, vectorSearch);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to query OpenSearch", exception);
        }
    }

    private List<Double> embed(String inputText) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "inputText", inputText,
                    "dimensions", properties.embeddingDimensions(),
                    "normalize", properties.normalizeEmbeddings()
            ));
            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(InvokeModelRequest.builder()
                    .modelId(properties.embeddingModelId())
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(payload))
                    .build());
            JsonNode embeddingNode = objectMapper.readTree(response.body().asUtf8String()).path("embedding");
            List<Double> embedding = new ArrayList<>(embeddingNode.size());
            embeddingNode.forEach(node -> embedding.add(node.asDouble()));
            if (embedding.isEmpty()) {
                throw new IllegalStateException("Bedrock returned an empty embedding");
            }
            return List.copyOf(embedding);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to parse Bedrock embedding response", exception);
        }
    }

    private List<FallbackDocument> loadFallbackCorpus() {
        try (InputStream stream = RegulatorySearchClient.class.getClassLoader()
                .getResourceAsStream("regulatory/regulatory_corpus.jsonl")) {
            if (stream == null) {
                log.warn("bundled regulatory corpus not found on classpath");
                return List.of();
            }

            List<FallbackDocument> documents = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        continue;
                    }
                    JsonNode node = objectMapper.readTree(line);
                    documents.add(new FallbackDocument(
                            node.path("control_id").asText(),
                            node.path("title").asText(),
                            ApplicableFramework.valueOf(node.path("framework").asText()),
                            mapPillar(node.path("pillar").asText()),
                            node.path("summary").asText(),
                            node.path("source_reference").asText(),
                            node.path("source_url").asText(),
                            readArray(node.path("sector")),
                            normalizeText(
                                    node.path("title").asText(),
                                    node.path("summary").asText(),
                                    joinArray(node.path("obligations")),
                                    joinArray(node.path("keywords")),
                                    node.path("source_reference").asText()
                            )
                    ));
                }
            }
            return List.copyOf(documents);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to load bundled regulatory corpus", exception);
        }
    }

    private SignedResponse signedRequest(URI uri, String body) throws IOException {
        SdkHttpFullRequest unsignedRequest = SdkHttpFullRequest.builder()
                .method(SdkHttpMethod.POST)
                .uri(uri)
                .putHeader("content-type", "application/json")
                .contentStreamProvider(() -> new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)))
                .build();

        AwsCredentials credentials = credentialsProvider.resolveCredentials();
        SdkHttpFullRequest signedRequest = SIGNER.sign(
                unsignedRequest,
                Aws4SignerParams.builder()
                        .signingName("aoss")
                        .signingRegion(Region.of(properties.region()))
                        .awsCredentials(credentials)
                        .build()
        );

        HttpsURLConnection connection = (HttpsURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        signedRequest.headers().forEach((name, values) -> values.forEach(value -> connection.addRequestProperty(name, value)));

        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream responseStream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = responseStream == null ? "" : new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
        connection.disconnect();
        return new SignedResponse(statusCode, responseBody);
    }

    private Map<String, Object> buildLexicalSearchQuery(String queryText, String sector, int limit) {
        Map<String, Object> multiMatch = new LinkedHashMap<>();
        multiMatch.put("query", queryText);
        multiMatch.put("fields", List.of("title^4", "summary^3", "obligations^2", "keywords", "source_reference"));

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("size", Math.min(limit, properties.maxResults()));
        query.put("_source", searchSourceFields());
        query.put("query", Map.of(
                "bool", Map.of(
                        "filter", List.of(Map.of("term", Map.of("sector", sector))),
                        "must", List.of(Map.of("multi_match", multiMatch))
                )
        ));
        return query;
    }

    private Map<String, Object> buildVectorSearchQuery(List<Double> queryEmbedding, int limit) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("size", Math.min(limit, Math.max(properties.maxResults(), properties.vectorCandidates())));
        query.put("_source", searchSourceFields());
        query.put("query", Map.of(
                "knn", Map.of(
                        "embedding", Map.of(
                                "vector", queryEmbedding,
                                "k", Math.min(limit, Math.max(properties.maxResults(), properties.vectorCandidates()))
                        )
                )
        ));
        return query;
    }

    private List<String> searchSourceFields() {
        return List.of(
                "framework",
                "pillar",
                "control_id",
                "title",
                "summary",
                "obligations",
                "source_url",
                "source_reference",
                "sector"
        );
    }

    private List<RegulatoryMatch> parseMatches(String responseBody, String sector, boolean vectorSearch) throws IOException {
        JsonNode hitNodes = objectMapper.readTree(responseBody).path("hits").path("hits");
        List<RegulatoryMatch> matches = new ArrayList<>();
        for (JsonNode hitNode : hitNodes) {
            JsonNode source = hitNode.path("_source");
            if (!sectorMatches(source.path("sector"), sector)) {
                continue;
            }
            matches.add(new RegulatoryMatch(
                    source.path("control_id").asText(),
                    source.path("title").asText(),
                    ApplicableFramework.valueOf(source.path("framework").asText()),
                    mapPillar(source.path("pillar").asText()),
                    source.path("summary").asText(),
                    source.path("source_reference").asText(),
                    source.path("source_url").asText(),
                    hitNode.path("_score").asDouble(),
                    vectorSearch
            ));
        }
        return matches;
    }

    private boolean sectorMatches(JsonNode sectorNode, String sector) {
        if (sectorNode.isArray()) {
            for (JsonNode node : sectorNode) {
                if (sector.equalsIgnoreCase(node.asText())) {
                    return true;
                }
            }
            return false;
        }
        return sector.equalsIgnoreCase(sectorNode.asText());
    }

    private List<RegulatoryMatch> mergeMatches(List<RegulatoryMatch> lexicalMatches, List<RegulatoryMatch> vectorMatches, int limit) {
        double maxLexical = lexicalMatches.stream().mapToDouble(RegulatoryMatch::score).max().orElse(1.0d);
        double maxVector = vectorMatches.stream().mapToDouble(RegulatoryMatch::score).max().orElse(1.0d);

        Map<String, HybridAccumulator> accumulators = new LinkedHashMap<>();
        lexicalMatches.forEach(match -> accumulators
                .computeIfAbsent(match.controlId(), ignored -> new HybridAccumulator(match))
                .addLexical(scoreFraction(match.score(), maxLexical)));
        vectorMatches.forEach(match -> accumulators
                .computeIfAbsent(match.controlId(), ignored -> new HybridAccumulator(match))
                .addVector(scoreFraction(match.score(), maxVector)));

        return accumulators.values().stream()
                .map(accumulator -> accumulator.toMatch(properties.lexicalWeight(), properties.vectorWeight()))
                .sorted(Comparator.comparingDouble(RegulatoryMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    private List<RegulatoryMatch> fallbackSearch(String queryText, String sector, int limit) {
        List<String> tokens = List.of(buildQueryText(queryText).split("\\s+")).stream()
                .filter(token -> !token.isBlank())
                .toList();
        if (tokens.isEmpty()) {
            return List.of();
        }

        return fallbackCorpus.stream()
                .filter(document -> document.sectors().contains(sector))
                .map(document -> toFallbackMatch(document, tokens))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingDouble(RegulatoryMatch::score).reversed())
                .limit(limit)
                .toList();
    }

    private double scoreFraction(double value, double maxValue) {
        if (maxValue <= 0.0d) {
            return 0.0d;
        }
        return value / maxValue;
    }

    private RegulatoryMatch toFallbackMatch(FallbackDocument document, List<String> tokens) {
        long matchedTokens = tokens.stream().filter(document.normalizedText()::contains).count();
        if (matchedTokens == 0) {
            return null;
        }
        double score = (double) matchedTokens / tokens.size();
        return new RegulatoryMatch(
                document.controlId(),
                document.title(),
                document.framework(),
                document.pillar(),
                document.summary(),
                document.sourceReference(),
                document.sourceUrl(),
                score,
                false
        );
    }

    private List<String> readArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        } else if (!node.isMissingNode() && !node.isNull()) {
            values.add(node.asText());
        }
        return List.copyOf(values);
    }

    private String joinArray(JsonNode node) {
        if (!node.isArray()) {
            return node.asText("");
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return String.join(" ", values);
    }

    private String normalizeText(String... values) {
        return List.of(values).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    private CompliancePillar mapPillar(String rawPillar) {
        return switch (rawPillar) {
            case "APPLICABILITY" -> CompliancePillar.APPLICABILITY;
            case "TRANSPARENCY", "HUMAN_OVERSIGHT" -> CompliancePillar.TRANSPARENCY_HUMAN_OVERSIGHT;
            case "ACCURACY_ROBUSTNESS_CYBERSECURITY" -> CompliancePillar.ACCURACY_ROBUSTNESS;
            default -> CompliancePillar.valueOf(rawPillar);
        };
    }

    private static final class HybridAccumulator {
        private RegulatoryMatch representative;
        private double lexicalScore;
        private double vectorScore;

        private HybridAccumulator(RegulatoryMatch representative) {
            this.representative = representative;
        }

        private void addLexical(double score) {
            lexicalScore = Math.max(lexicalScore, score);
        }

        private void addVector(double score) {
            vectorScore = Math.max(vectorScore, score);
        }

        private RegulatoryMatch toMatch(double lexicalWeight, double vectorWeight) {
            double hybridScore = (lexicalScore * lexicalWeight) + (vectorScore * vectorWeight);
            return representative.withScore(hybridScore, lexicalScore > 0.0d && vectorScore > 0.0d);
        }
    }

    public record RegulatoryMatch(
            String controlId,
            String title,
            ApplicableFramework framework,
            CompliancePillar pillar,
            String summary,
            String sourceReference,
            String sourceUrl,
            double score,
            boolean hybrid
    ) {
        private RegulatoryMatch withScore(double newScore, boolean newHybrid) {
            return new RegulatoryMatch(controlId, title, framework, pillar, summary, sourceReference, sourceUrl, newScore, newHybrid);
        }
    }

    private record SignedResponse(int statusCode, String body) {
    }

    private record FallbackDocument(
            String controlId,
            String title,
            ApplicableFramework framework,
            CompliancePillar pillar,
            String summary,
            String sourceReference,
            String sourceUrl,
            List<String> sectors,
            String normalizedText
    ) {
    }
}
