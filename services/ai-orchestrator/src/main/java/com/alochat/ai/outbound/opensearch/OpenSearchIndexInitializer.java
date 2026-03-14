package com.alochat.ai.outbound.opensearch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "alochat.ai.opensearch.endpoint")
public class OpenSearchIndexInitializer {

    private final OpenSearchClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String memoryIndex;

    public OpenSearchIndexInitializer(
            @Value("${alochat.ai.opensearch.endpoint}") String endpoint,
            @Value("${alochat.aws.region}") String region,
            @Value("${alochat.ai.opensearch.memory-index}") String memoryIndex
    ) {
        this.client = new OpenSearchClient(
                URI.create(endpoint),
                Region.of(region),
                DefaultCredentialsProvider.create()
        );
        this.memoryIndex = memoryIndex;
    }

    @PostConstruct
    public void ensureMemoryIndex() {
        OpenSearchClient.OpenSearchResponse head = client.head("/" + memoryIndex);
        if (head.isSuccess()) {
            return;
        }
        if (!head.isNotFound()) {
            throw new IllegalStateException("Unexpected OpenSearch response for HEAD: " + head.statusCode());
        }

        ObjectNode mapping = mapper.createObjectNode();
        ObjectNode properties = mapping.putObject("mappings").putObject("properties");
        properties.putObject("memoryKey").put("type", "keyword");
        properties.putObject("tenantId").put("type", "keyword");
        properties.putObject("userId").put("type", "keyword");
        properties.putObject("conversationId").put("type", "keyword");
        properties.putObject("summary").put("type", "text");
        properties.putObject("products").put("type", "keyword");
        properties.putObject("tags").put("type", "keyword");
        properties.putObject("updatedAt").put("type", "date");

        OpenSearchClient.OpenSearchResponse created = client.put("/" + memoryIndex, mapping.toString());
        if (!created.isSuccess()) {
            throw new IllegalStateException("Unable to create memory index: " + created.statusCode() + " " + created.body());
        }
    }
}
