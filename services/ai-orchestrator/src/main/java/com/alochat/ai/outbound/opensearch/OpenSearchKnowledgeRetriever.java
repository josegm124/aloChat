package com.alochat.ai.outbound.opensearch;

import com.alochat.ai.model.KnowledgeSnippet;
import com.alochat.ai.port.KnowledgeRetriever;
import com.alochat.contracts.message.MessageEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Repository
@ConditionalOnProperty(name = "alochat.ai.opensearch.endpoint")
public class OpenSearchKnowledgeRetriever implements KnowledgeRetriever {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchKnowledgeRetriever.class);

    private final OpenSearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String index;

    public OpenSearchKnowledgeRetriever(
            @Value("${alochat.ai.opensearch.endpoint}") String endpoint,
            @Value("${alochat.aws.region}") String region,
            @Value("${alochat.ai.opensearch.knowledge-index}") String index
    ) {
        this.client = new OpenSearchClient(
                URI.create(endpoint),
                Region.of(region),
                DefaultCredentialsProvider.create()
        );
        this.index = index;
    }

    @Override
    public List<KnowledgeSnippet> retrieve(MessageEnvelope envelope, int limit) {
        String question = safeText(envelope.content().text());
        if (question.isBlank()) {
            return List.of();
        }

        String tenantId = safeText(envelope.tenantId());
        ObjectNode body = mapper.createObjectNode();
        body.put("size", limit);

        ObjectNode queryNode = body.putObject("query").putObject("bool");
        ArrayNode should = queryNode.putArray("should");
        should.addObject().set("multi_match", buildMultiMatch(question, 4, 3, 2));

        String expandedQuestion = expandQuestion(question);
        if (!expandedQuestion.equalsIgnoreCase(question)) {
            should.addObject().set("multi_match", buildMultiMatch(expandedQuestion, 3, 4, 3));
        }
        queryNode.put("minimum_should_match", 1);

        if (!tenantId.isBlank()) {
            ArrayNode filter = queryNode.putArray("filter");
            ObjectNode term = mapper.createObjectNode();
            term.put("tenantId", tenantId);
            filter.addObject().set("term", term);
        }

        OpenSearchClient.OpenSearchResponse response = client.post("/" + index + "/_search", body.toString());
        if (!response.isSuccess()) {
            log.warn("OpenSearch knowledge search failed messageId={} status={} body={}",
                    envelope.messageId(), response.statusCode(), response.body());
            return List.of();
        }

        return parseSnippets(response.body());
    }

    private ObjectNode buildMultiMatch(String query, int productBoost, int categoryBoost, int usageBoost) {
        ObjectNode multiMatch = mapper.createObjectNode();
        multiMatch.put("query", query);
        multiMatch.put("type", "best_fields");
        ArrayNode fields = multiMatch.putArray("fields");
        fields.add("productName^" + productBoost);
        fields.add("category^" + categoryBoost);
        fields.add("usage^" + usageBoost);
        fields.add("itemId");
        return multiMatch;
    }

    private String expandQuestion(String question) {
        String normalized = safeText(question).toLowerCase(Locale.ROOT);
        Set<String> tokens = new LinkedHashSet<>();
        tokens.add(question);

        if (containsAny(normalized, "pintura", "pintar", "recubrimiento")) {
            tokens.add("pintura recubrimiento acabado");
        }
        if (containsAny(normalized, "pared", "muro", "cuarto", "recamara", "sala", "comedor", "interior", "hogar")) {
            tokens.add("pintura interior pared muro hogar vinil acrilica");
        }
        if (containsAny(normalized, "fachada", "exterior", "frente", "patio")) {
            tokens.add("pintura exterior fachada intemperie");
        }
        if (containsAny(normalized, "techo", "azotea", "humedad", "goteras")) {
            tokens.add("impermeabilizante techo azotea humedad");
        }
        if (containsAny(normalized, "herrer", "reja", "barandal", "metal")) {
            tokens.add("pintura herreria esmalte metal anticorrosivo");
        }
        if (containsAny(normalized, "madera", "puerta", "barniz")) {
            tokens.add("pintura madera barniz esmalte");
        }

        return String.join(" ", tokens);
    }

    private List<KnowledgeSnippet> parseSnippets(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray()) {
                return List.of();
            }
            List<KnowledgeSnippet> snippets = new ArrayList<>();
            for (JsonNode hit : hitsNode) {
                JsonNode source = hit.path("_source");
                String productName = source.path("productName").asText("");
                String usage = source.path("usage").asText("");
                String category = source.path("category").asText("");
                String price = source.path("priceMxn").asText("");
                String tenantId = source.path("tenantId").asText("");
                double score = hit.path("_score").asDouble(0.0);

                Map<String, String> metadata = new LinkedHashMap<>();
                if (!productName.isBlank()) {
                    metadata.put("productName", productName);
                }
                if (!category.isBlank()) {
                    metadata.put("category", category);
                }
                if (!price.isBlank()) {
                    metadata.put("priceMxn", price);
                }
                if (!usage.isBlank()) {
                    metadata.put("usage", usage);
                }
                if (!tenantId.isBlank()) {
                    metadata.put("tenantId", tenantId);
                }

                snippets.add(new KnowledgeSnippet(
                        hit.path("_id").asText(),
                        productName,
                        usage,
                        "opensearch",
                        index,
                        score,
                        Map.copyOf(metadata)
                ));
            }
            return List.copyOf(snippets);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse OpenSearch response", exception);
        }
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private boolean containsAny(String input, String... tokens) {
        for (String token : tokens) {
            if (input.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
