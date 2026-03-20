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
        String preferredLanguage = resolvePreferredLanguage(envelope);
        boolean asksPromotion = asksPromotion(question);
        ObjectNode body = mapper.createObjectNode();
        body.put("size", limit);

        ObjectNode queryNode = body.putObject("query").putObject("bool");
        ArrayNode should = queryNode.putArray("should");
        should.addObject().set("multi_match", buildMultiMatch(question, 4, 3, 2));

        String expandedQuestion = expandQuestion(question);
        if (!expandedQuestion.equalsIgnoreCase(question)) {
            should.addObject().set("multi_match", buildMultiMatch(expandedQuestion, 3, 4, 3));
        }
        if (!preferredLanguage.isBlank()) {
            should.addObject().set("term", buildBoostedTerm("language", preferredLanguage, 5));
        }
        if (asksPromotion) {
            should.addObject().set("term", buildBoostedTerm("docType", "offer", 7));
            should.addObject().set("term", buildBoostedTerm("offerActive", true, 6));
        } else {
            should.addObject().set("term", buildBoostedTerm("docType", "product", 2));
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
        fields.add("offerTitle^5");
        fields.add("offerDescription^4");
        fields.add("promotionType^3");
        fields.add("searchText^2");
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
        if (containsAny(normalized, "discount", "offer", "promotion", "sale", "deal", "descuento", "oferta", "promocion", "rebaja")) {
            tokens.add("descuento oferta promocion rebaja sale discount offer promotion");
        }

        return String.join(" ", tokens);
    }

    private ObjectNode buildBoostedTerm(String field, String value, int boost) {
        ObjectNode wrapper = mapper.createObjectNode();
        ObjectNode termNode = mapper.createObjectNode();
        termNode.put("value", value);
        termNode.put("boost", boost);
        wrapper.set(field, termNode);
        return wrapper;
    }

    private ObjectNode buildBoostedTerm(String field, boolean value, int boost) {
        ObjectNode wrapper = mapper.createObjectNode();
        ObjectNode termNode = mapper.createObjectNode();
        termNode.put("value", value);
        termNode.put("boost", boost);
        wrapper.set(field, termNode);
        return wrapper;
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
                String regularPrice = source.path("regularPriceMxn").asText("");
                String promoPrice = source.path("promoPriceMxn").asText("");
                String tenantId = source.path("tenantId").asText("");
                String unit = source.path("unit").asText("");
                String language = source.path("language").asText("");
                String docType = source.path("docType").asText("");
                String promotionType = source.path("promotionType").asText("");
                String offerTitle = source.path("offerTitle").asText("");
                String offerDescription = source.path("offerDescription").asText("");
                String validFrom = source.path("validFrom").asText("");
                String validUntil = source.path("validUntil").asText("");
                boolean offerActive = source.path("offerActive").asBoolean(false);
                double score = hit.path("_score").asDouble(0.0);
                String effectivePrice = !price.isBlank() ? price : (!promoPrice.isBlank() ? promoPrice : regularPrice);
                String excerpt = !offerDescription.isBlank() ? offerDescription : usage;
                String title = !offerTitle.isBlank() ? productName : productName;

                Map<String, String> metadata = new LinkedHashMap<>();
                if (!productName.isBlank()) {
                    metadata.put("productName", productName);
                }
                if (!category.isBlank()) {
                    metadata.put("category", category);
                }
                if (!effectivePrice.isBlank()) {
                    metadata.put("priceMxn", effectivePrice);
                }
                if (!usage.isBlank()) {
                    metadata.put("usage", usage);
                }
                if (!unit.isBlank()) {
                    metadata.put("unit", unit);
                }
                if (!tenantId.isBlank()) {
                    metadata.put("tenantId", tenantId);
                }
                if (!language.isBlank()) {
                    metadata.put("language", language);
                }
                if (!docType.isBlank()) {
                    metadata.put("docType", docType);
                }
                if (!regularPrice.isBlank()) {
                    metadata.put("regularPriceMxn", regularPrice);
                }
                if (!promoPrice.isBlank()) {
                    metadata.put("promoPriceMxn", promoPrice);
                }
                if (!promotionType.isBlank()) {
                    metadata.put("promotionType", promotionType);
                }
                if (!offerTitle.isBlank()) {
                    metadata.put("offerTitle", offerTitle);
                }
                if (!offerDescription.isBlank()) {
                    metadata.put("offerDescription", offerDescription);
                }
                if (!validFrom.isBlank()) {
                    metadata.put("validFrom", validFrom);
                }
                if (!validUntil.isBlank()) {
                    metadata.put("validUntil", validUntil);
                }
                if (offerActive) {
                    metadata.put("offerActive", "true");
                }

                snippets.add(new KnowledgeSnippet(
                        hit.path("_id").asText(),
                        title,
                        excerpt,
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

    private String resolvePreferredLanguage(MessageEnvelope envelope) {
        String preferredLanguage = safeText(envelope.metadata().get("preferredLanguage")).toLowerCase(Locale.ROOT);
        if (preferredLanguage.startsWith("en")) {
            return "en";
        }
        if (preferredLanguage.startsWith("es")) {
            return "es";
        }
        String locale = safeText(envelope.metadata().get("locale")).toLowerCase(Locale.ROOT);
        if (locale.startsWith("en")) {
            return "en";
        }
        if (locale.startsWith("es")) {
            return "es";
        }
        return "";
    }

    private boolean asksPromotion(String question) {
        String normalized = safeText(question).toLowerCase(Locale.ROOT);
        return containsAny(normalized, "discount", "offer", "promotion", "sale", "deal",
                "descuento", "oferta", "promocion", "rebaja");
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
