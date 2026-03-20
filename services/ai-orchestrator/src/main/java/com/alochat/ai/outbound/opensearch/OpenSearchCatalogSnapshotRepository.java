package com.alochat.ai.outbound.opensearch;

import com.alochat.ai.model.StoreCatalogItem;
import com.alochat.ai.port.CatalogSnapshotRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

@Repository
@ConditionalOnProperty(name = "alochat.ai.opensearch.endpoint")
public class OpenSearchCatalogSnapshotRepository implements CatalogSnapshotRepository {

    private final OpenSearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String index;

    public OpenSearchCatalogSnapshotRepository(
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
    public Map<String, StoreCatalogItem> findByProductNames(String tenantId, List<String> productNames) {
        List<String> normalizedNames = productNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        if (normalizedNames.isEmpty()) {
            return Map.of();
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("size", Math.max(normalizedNames.size(), 10));
        ObjectNode queryNode = body.putObject("query").putObject("bool");
        ArrayNode should = queryNode.putArray("should");
        for (String productName : normalizedNames) {
            ObjectNode matchPhrase = mapper.createObjectNode();
            matchPhrase.put("productName", productName);
            should.addObject().set("match_phrase", matchPhrase);
        }
        queryNode.put("minimum_should_match", 1);
        if (tenantId != null && !tenantId.isBlank()) {
            ArrayNode filter = queryNode.putArray("filter");
            ObjectNode term = mapper.createObjectNode();
            term.put("tenantId", tenantId);
            filter.addObject().set("term", term);
        }

        OpenSearchClient.OpenSearchResponse response = client.post("/" + index + "/_search", body.toString());
        if (!response.isSuccess()) {
            return Map.of();
        }

        return parseItems(response.body());
    }

    private Map<String, StoreCatalogItem> parseItems(String payload) {
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode hitsNode = root.path("hits").path("hits");
            if (!hitsNode.isArray()) {
                return Map.of();
            }

            Map<String, StoreCatalogItem> items = new LinkedHashMap<>();
            for (JsonNode hit : hitsNode) {
                JsonNode source = hit.path("_source");
                String productName = source.path("productName").asText("");
                if (productName.isBlank()) {
                    continue;
                }
                items.putIfAbsent(productName, new StoreCatalogItem(
                        source.path("itemId").asText(""),
                        productName,
                        source.path("category").asText(""),
                        source.path("priceMxn").asText(""),
                        source.path("unit").asText(""),
                        source.path("usage").asText("")
                ));
            }
            return Map.copyOf(items);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to parse OpenSearch catalog snapshot response", exception);
        }
    }
}
