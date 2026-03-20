package com.alochat.ai.outbound;

import com.alochat.ai.model.KnowledgeSnippet;
import com.alochat.ai.model.StoreCatalogItem;
import com.alochat.ai.port.CatalogSnapshotRepository;
import com.alochat.ai.port.KnowledgeRetriever;
import com.alochat.contracts.message.MessageEnvelope;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnMissingBean(KnowledgeRetriever.class)
public class ClasspathStoreKnowledgeRetriever implements KnowledgeRetriever, CatalogSnapshotRepository {

    private final String catalogResource;
    private final AtomicReference<List<StoreCatalogItem>> catalogCache = new AtomicReference<>();

    public ClasspathStoreKnowledgeRetriever(
            @Value("${alochat.ai.knowledge.catalog-resource}") String catalogResource
    ) {
        this.catalogResource = catalogResource;
    }

    @Override
    public List<KnowledgeSnippet> retrieve(MessageEnvelope envelope, int limit) {
        String question = safeText(envelope.content().text());
        List<String> tokens = tokenize(question);
        return loadCatalog().stream()
                .map(item -> toSnippet(item, question, tokens))
                .filter(snippet -> snippet.score() > 0)
                .sorted(Comparator.comparingDouble(KnowledgeSnippet::score).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public Map<String, StoreCatalogItem> findByProductNames(String tenantId, List<String> productNames) {
        List<String> normalizedProducts = productNames.stream()
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .toList();
        Map<String, StoreCatalogItem> found = new LinkedHashMap<>();
        for (StoreCatalogItem item : loadCatalog()) {
            String normalizedProductName = normalize(item.productName());
            if (normalizedProducts.contains(normalizedProductName)) {
                found.put(item.productName(), item);
            }
        }
        return Map.copyOf(found);
    }

    private KnowledgeSnippet toSnippet(StoreCatalogItem item, String question, List<String> tokens) {
        String searchable = normalize(item.productName() + " " + item.category() + " " + item.usage());
        String normalizedQuestion = normalize(question);
        double score = 0;

        if (normalizedQuestion.contains(normalize(item.productName()))) {
            score += 100;
        }
        if (normalizedQuestion.contains(normalize(item.category()))) {
            score += 40;
        }

        for (String token : tokens) {
            if (token.length() < 3) {
                continue;
            }
            if (searchable.contains(token)) {
                score += 12;
            }
        }

        if (normalizedQuestion.contains("casa") && searchable.contains("casa")) {
            score += 20;
        }
        if (normalizedQuestion.contains("bodega") && searchable.contains("bodega")) {
            score += 20;
        }
        if (normalizedQuestion.contains("techo") && (searchable.contains("techo") || searchable.contains("imperme"))) {
            score += 20;
        }
        if (normalizedQuestion.contains("interior") && searchable.contains("interior")) {
            score += 20;
        }
        if (normalizedQuestion.contains("exterior") && searchable.contains("exterior")) {
            score += 20;
        }

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("productName", item.productName());
        metadata.put("category", item.category());
        metadata.put("priceMxn", item.priceMxn());
        metadata.put("unit", item.unit());
        metadata.put("usage", item.usage());
        metadata.put("language", "es");
        metadata.put("docType", "product");

        return new KnowledgeSnippet(
                item.itemId(),
                item.productName(),
                item.usage(),
                "store-catalog",
                catalogResource,
                score,
                Map.copyOf(metadata)
        );
    }

    private List<StoreCatalogItem> loadCatalog() {
        List<StoreCatalogItem> existing = catalogCache.get();
        if (existing != null) {
            return existing;
        }

        List<StoreCatalogItem> loaded = new ArrayList<>();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(stripClasspathPrefix(catalogResource))) {
            if (inputStream == null) {
                throw new IllegalStateException("Catalog resource not found: " + catalogResource);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                boolean skipHeader = true;
                while ((line = reader.readLine()) != null) {
                    if (skipHeader) {
                        skipHeader = false;
                        continue;
                    }
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] parts = line.split("\\|", -1);
                    if (parts.length < 6) {
                        continue;
                    }
                    loaded.add(new StoreCatalogItem(
                            parts[0].trim(),
                            parts[1].trim(),
                            parts[2].trim(),
                            parts[3].trim(),
                            parts[4].trim(),
                            parts[5].trim()
                    ));
                }
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load store catalog knowledge base", exception);
        }

        catalogCache.compareAndSet(null, List.copyOf(loaded));
        return catalogCache.get();
    }

    private List<String> tokenize(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split("\\s+"));
    }

    private String normalize(String value) {
        String normalized = Normalizer.normalize(safeText(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeText(String value) {
        return value == null ? "" : value;
    }

    private String stripClasspathPrefix(String value) {
        return value.startsWith("classpath:") ? value.substring("classpath:".length()) : value;
    }
}
