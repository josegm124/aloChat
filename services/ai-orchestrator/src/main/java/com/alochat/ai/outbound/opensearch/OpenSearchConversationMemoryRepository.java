package com.alochat.ai.outbound.opensearch;

import com.alochat.ai.model.ConversationMemory;
import com.alochat.ai.port.ConversationMemoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Repository
@ConditionalOnProperty(name = "alochat.ai.opensearch.endpoint")
public class OpenSearchConversationMemoryRepository implements ConversationMemoryRepository {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchConversationMemoryRepository.class);

    private final OpenSearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String index;

    public OpenSearchConversationMemoryRepository(
            @Value("${alochat.ai.opensearch.endpoint}") String endpoint,
            @Value("${alochat.aws.region}") String region,
            @Value("${alochat.ai.opensearch.memory-index}") String index
    ) {
        this.client = new OpenSearchClient(
                URI.create(endpoint),
                Region.of(region),
                DefaultCredentialsProvider.create()
        );
        this.index = index;
    }

    @Override
    public Optional<ConversationMemory> findByMemoryKey(String memoryKey) {
        if (memoryKey == null || memoryKey.isBlank()) {
            return Optional.empty();
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("size", 1);
        body.putArray("sort")
                .addObject()
                .putObject("updatedAt")
                .put("order", "desc");

        ObjectNode query = body.putObject("query").putObject("term");
        query.put("memoryKey", memoryKey);

        OpenSearchClient.OpenSearchResponse response = client.post("/" + index + "/_search", body.toString());
        if (!response.isSuccess()) {
            log.warn("OpenSearch memory search failed memoryKey={} status={} body={}",
                    memoryKey, response.statusCode(), response.body());
            return Optional.empty();
        }

        return parseFirst(response.body());
    }

    @Override
    public void save(ConversationMemory conversationMemory) {
        if (conversationMemory == null) {
            return;
        }

        ObjectNode doc = mapper.createObjectNode();
        doc.put("memoryKey", safe(conversationMemory.memoryKey()));
        doc.put("tenantId", safe(conversationMemory.tenantId()));
        doc.put("channel", safe(conversationMemory.channel()));
        doc.put("conversationId", safe(conversationMemory.conversationId()));
        doc.put("userId", safe(conversationMemory.userId()));
        doc.put("summary", safe(conversationMemory.summary()));
        doc.put("lastQuestion", safe(conversationMemory.lastQuestion()));
        doc.put("lastInteractionAt", conversationMemory.lastInteractionAt().toString());
        doc.put("updatedAt", conversationMemory.lastInteractionAt().toString());
        if (conversationMemory.followUpAt() != null) {
            doc.put("followUpAt", conversationMemory.followUpAt().toString());
        }
        doc.put("followUpReason", safe(conversationMemory.followUpReason()));

        ArrayNode products = doc.putArray("products");
        conversationMemory.trackedProducts().forEach(products::add);
        ArrayNode tags = doc.putArray("tags");
        conversationMemory.interestTags().forEach(tags::add);

        OpenSearchClient.OpenSearchResponse response = client.post("/" + index + "/_doc", doc.toString());
        if (!response.isSuccess()) {
            log.warn("OpenSearch memory write failed memoryKey={} status={} body={}",
                    conversationMemory.memoryKey(), response.statusCode(), response.body());
        }
    }

    private Optional<ConversationMemory> parseFirst(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode hits = root.path("hits").path("hits");
            if (!hits.isArray() || hits.isEmpty()) {
                return Optional.empty();
            }
            JsonNode source = hits.get(0).path("_source");
            return Optional.of(new ConversationMemory(
                    source.path("memoryKey").asText(""),
                    source.path("tenantId").asText(""),
                    source.path("channel").asText(""),
                    source.path("conversationId").asText(""),
                    source.path("userId").asText(""),
                    source.path("summary").asText(""),
                    source.path("lastQuestion").asText(""),
                    readList(source.path("products")),
                    readList(source.path("tags")),
                    parseInstant(source.path("lastInteractionAt")),
                    parseNullableInstant(source.path("followUpAt")),
                    source.path("followUpReason").asText("")
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse OpenSearch memory response", exception);
        }
    }

    private List<String> readList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText("")));
        return values;
    }

    private Instant parseInstant(JsonNode node) {
        String value = node.asText("");
        return value.isBlank() ? Instant.EPOCH : Instant.parse(value);
    }

    private Instant parseNullableInstant(JsonNode node) {
        String value = node.asText("");
        return value.isBlank() ? null : Instant.parse(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
