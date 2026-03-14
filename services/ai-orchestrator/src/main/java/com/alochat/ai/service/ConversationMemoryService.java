package com.alochat.ai.service;

import com.alochat.ai.model.CampaignHint;
import com.alochat.ai.model.ConversationMemory;
import com.alochat.ai.model.KnowledgeSnippet;
import com.alochat.contracts.message.MessageEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ConversationMemoryService {

    private static final long FIVE_YEARS_IN_SECONDS = 157_680_000L;

    private final ConversationMemoryKeyService conversationMemoryKeyService;

    public ConversationMemoryService(ConversationMemoryKeyService conversationMemoryKeyService) {
        this.conversationMemoryKeyService = conversationMemoryKeyService;
    }

    public ConversationMemory merge(
            MessageEnvelope inboundEnvelope,
            ConversationMemory existingMemory,
            List<KnowledgeSnippet> knowledgeSnippets
    ) {
        String memoryKey = conversationMemoryKeyService.build(inboundEnvelope);
        String question = safeText(inboundEnvelope.content().text());
        Set<String> trackedProducts = new LinkedHashSet<>();
        Set<String> interestTags = new LinkedHashSet<>();

        if (existingMemory != null) {
            trackedProducts.addAll(existingMemory.trackedProducts());
            interestTags.addAll(existingMemory.interestTags());
        }

        trackedProducts.addAll(extractProducts(knowledgeSnippets));
        interestTags.addAll(extractInterestTags(question, knowledgeSnippets));

        String summary = buildSummary(existingMemory, question, knowledgeSnippets);
        Instant followUpAt = extractProjectIntent(question) ? inboundEnvelope.receivedAt().plusSeconds(FIVE_YEARS_IN_SECONDS) : null;
        String followUpReason = followUpAt == null
                ? null
                : "Recordatorio de recompra o repintado estimado 5 anos despues del proyecto";

        return new ConversationMemory(
                memoryKey,
                safeValue(inboundEnvelope.tenantId(), "default"),
                inboundEnvelope.channel().name(),
                safeValue(inboundEnvelope.conversationId(), inboundEnvelope.messageId()),
                safeValue(inboundEnvelope.userId(), inboundEnvelope.conversationId()),
                summary,
                question,
                List.copyOf(trackedProducts),
                List.copyOf(interestTags),
                inboundEnvelope.receivedAt(),
                followUpAt,
                followUpReason
        );
    }

    public CampaignHint createHint(MessageEnvelope inboundEnvelope, ConversationMemory conversationMemory) {
        if (conversationMemory == null) {
            return null;
        }
        if (conversationMemory.followUpAt() != null) {
            return new CampaignHint(
                    inboundEnvelope.messageId() + "#repaint-followup",
                    conversationMemory.memoryKey(),
                    conversationMemory.tenantId(),
                    conversationMemory.channel(),
                    conversationMemory.conversationId(),
                    conversationMemory.userId(),
                    "REPLENISHMENT_FOLLOW_UP",
                    safeValue(conversationMemory.followUpReason(), "Seguimiento de proyecto de pintura"),
                    conversationMemory.trackedProducts(),
                    inboundEnvelope.receivedAt(),
                    conversationMemory.followUpAt()
            );
        }
        if (!conversationMemory.trackedProducts().isEmpty()) {
            return new CampaignHint(
                    inboundEnvelope.messageId() + "#discount-watch",
                    conversationMemory.memoryKey(),
                    conversationMemory.tenantId(),
                    conversationMemory.channel(),
                    conversationMemory.conversationId(),
                    conversationMemory.userId(),
                    "DISCOUNT_WATCH",
                    "Avisar si alguno de los productos de interes entra en promocion",
                    conversationMemory.trackedProducts(),
                    inboundEnvelope.receivedAt(),
                    inboundEnvelope.receivedAt()
            );
        }
        return null;
    }

    private List<String> extractProducts(List<KnowledgeSnippet> knowledgeSnippets) {
        return knowledgeSnippets.stream()
                .map(snippet -> snippet.metadata().getOrDefault("productName", ""))
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(4)
                .toList();
    }

    private List<String> extractInterestTags(String question, List<KnowledgeSnippet> knowledgeSnippets) {
        Set<String> tags = new LinkedHashSet<>();
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        addIfContains(normalizedQuestion, tags, "casa", "hogar");
        addIfContains(normalizedQuestion, tags, "bodega", "bodega");
        addIfContains(normalizedQuestion, tags, "interior", "interior");
        addIfContains(normalizedQuestion, tags, "exterior", "exterior");
        addIfContains(normalizedQuestion, tags, "techo", "impermeabilizacion");
        addIfContains(normalizedQuestion, tags, "imperme", "impermeabilizacion");
        addIfContains(normalizedQuestion, tags, "precio", "precio");
        addIfContains(normalizedQuestion, tags, "descuento", "promocion");
        knowledgeSnippets.stream()
                .map(snippet -> snippet.metadata().getOrDefault("category", ""))
                .filter(value -> !value.isBlank())
                .map(this::normalizeTag)
                .forEach(tags::add);
        return List.copyOf(tags);
    }

    private String buildSummary(ConversationMemory existingMemory, String question, List<KnowledgeSnippet> knowledgeSnippets) {
        List<String> products = extractProducts(knowledgeSnippets);
        List<String> summaryParts = new ArrayList<>();
        if (existingMemory != null && existingMemory.summary() != null && !existingMemory.summary().isBlank()) {
            summaryParts.add(existingMemory.summary());
        }
        summaryParts.add("Ultima consulta: " + question);
        if (!products.isEmpty()) {
            summaryParts.add("Productos relacionados: " + String.join(", ", products));
        }
        String summary = summaryParts.stream()
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(". "));
        return summary.length() > 600 ? summary.substring(summary.length() - 600) : summary;
    }

    private boolean extractProjectIntent(String question) {
        String normalized = question.toLowerCase(Locale.ROOT);
        return normalized.contains("pintar mi casa")
                || normalized.contains("pintar la casa")
                || normalized.contains("pintar mi bodega")
                || normalized.contains("pintar la bodega")
                || normalized.contains("repintar")
                || normalized.contains("quiero pintar");
    }

    private void addIfContains(String input, Set<String> tags, String token, String tag) {
        if (input.contains(token)) {
            tags.add(tag);
        }
    }

    private String normalizeTag(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replace(" ", "-")
                .replace("/", "-");
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }

    private String safeValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
