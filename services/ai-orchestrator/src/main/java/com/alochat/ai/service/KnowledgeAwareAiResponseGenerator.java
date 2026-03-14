package com.alochat.ai.service;

import com.alochat.ai.model.AiContext;
import com.alochat.ai.model.AiGenerationResult;
import com.alochat.ai.model.CampaignHint;
import com.alochat.ai.model.ConversationMemory;
import com.alochat.ai.model.KnowledgeSnippet;
import com.alochat.ai.port.AiResponseGenerator;
import com.alochat.contracts.message.ContentType;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.contracts.message.NormalizedContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeAwareAiResponseGenerator implements AiResponseGenerator {

    private final ConversationMemoryService conversationMemoryService;

    public KnowledgeAwareAiResponseGenerator(ConversationMemoryService conversationMemoryService) {
        this.conversationMemoryService = conversationMemoryService;
    }

    @Override
    public AiGenerationResult generate(MessageEnvelope envelope, AiContext context) {
        List<KnowledgeSnippet> snippets = context.knowledgeSnippets();
        String responseText = composeResponse(envelope, context.conversationMemory(), snippets);
        ConversationMemory updatedMemory = conversationMemoryService.merge(envelope, context.conversationMemory(), snippets);
        CampaignHint campaignHint = conversationMemoryService.createHint(envelope, updatedMemory);

        Map<String, String> metadata = new LinkedHashMap<>(envelope.metadata());
        metadata.put("aiMode", "knowledge-memory-stub");
        metadata.put("aiProvider", "catalog-heuristics");
        metadata.put("memoryKey", updatedMemory.memoryKey());
        metadata.put("knowledgeMatches", String.valueOf(snippets.size()));
        if (!snippets.isEmpty()) {
            metadata.put("topKnowledgeSource", snippets.getFirst().sourceRef());
        }

        MessageEnvelope responseEnvelope = envelope
                .withContent(new NormalizedContent(ContentType.TEXT, responseText))
                .withMetadata(metadata)
                .withStatus(MessageStatus.AI_COMPLETED);

        return new AiGenerationResult(responseEnvelope, updatedMemory, campaignHint);
    }

    private String composeResponse(
            MessageEnvelope envelope,
            ConversationMemory conversationMemory,
            List<KnowledgeSnippet> snippets
    ) {
        String question = safeText(envelope.content().text()).toLowerCase(Locale.ROOT);
        List<KnowledgeSnippet> rankedSnippets = rankSnippets(question, snippets);
        if (rankedSnippets.isEmpty()) {
            return "No encontre una coincidencia clara en el inventario actual. "
                    + "Si me dices si buscas interior, exterior, herreria, techo o un producto especifico, te respondo con una recomendacion mas precisa.";
        }

        KnowledgeSnippet top = rankedSnippets.getFirst();
        String productName = top.metadata().getOrDefault("productName", top.title());
        String price = top.metadata().getOrDefault("priceMxn", "sin precio");
        String unit = top.metadata().getOrDefault("unit", "");
        String usage = top.metadata().getOrDefault("usage", top.excerpt());
        boolean asksPrice = containsAny(question, "cuanto cuesta", "precio", "costo");
        boolean asksAvailability = containsAny(question, "tienes", "tiene", "hay", "disponible", "manejan");
        boolean asksDelivery = containsAny(question, "envio", "entrega", "cdmx", "edomex", "foraneo");
        boolean asksDiscount = containsAny(question, "descuento", "mayoreo", "inversionista");
        boolean asksRecommendation = containsAny(
                question,
                "que tipo",
                "cual usar",
                "me recomiendas",
                "quiero pintar",
                "dime una pintura",
                "dime que pintura",
                "para pintar",
                "pintar mi pared",
                "pared",
                "muro"
        );

        StringBuilder response = new StringBuilder();
        if (asksAvailability || asksPrice) {
            response.append("Si, en el inventario 2026 aparece ")
                    .append(productName)
                    .append(". Precio: ")
                    .append(price);
            if (!unit.isBlank()) {
                response.append(" por ").append(unit);
            }
            response.append(". Uso recomendado: ").append(usage).append(".");
        } else if (asksRecommendation) {
            response.append("Para lo que describes, estas son las opciones mas cercanas del inventario: ");
            response.append(buildRecommendations(rankedSnippets));
        } else {
            response.append("Con base en tu consulta, lo mas relevante del inventario es ")
                    .append(productName)
                    .append(" (")
                    .append(price);
            if (!unit.isBlank()) {
                response.append(" por ").append(unit);
            }
            response.append("). Uso recomendado: ").append(usage).append(".");
        }

        if (asksDelivery) {
            response.append(" Politicas de entrega: CDMX/EdoMex 24h, foraneo 48-96h, envio gratis arriba de $1,500 MXN y $250 MXN en menudeo.");
        }
        if (asksDiscount) {
            response.append(" Para compras mayores a $500k MXN aplica descuento de inversionista del 12%.");
        }
        if (conversationMemory != null && conversationMemory.summary() != null && !conversationMemory.summary().isBlank()) {
            response.append(" Tambien tengo contexto previo de esta conversacion para seguir recomendando productos relacionados.");
        }
        return response.toString();
    }

    private List<KnowledgeSnippet> rankSnippets(String question, List<KnowledgeSnippet> snippets) {
        return snippets.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeSnippet snippet) -> intentScore(question, snippet))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeSnippet::score).reversed()))
                .toList();
    }

    private int intentScore(String question, KnowledgeSnippet snippet) {
        String haystack = (
                safeText(snippet.title()) + " "
                        + safeText(snippet.excerpt()) + " "
                        + safeText(snippet.metadata().get("category"))
        ).toLowerCase(Locale.ROOT);

        int score = 0;
        if (containsAny(question, "pared", "muro", "interior", "cuarto", "recamara", "sala", "comedor")) {
            if (containsAny(haystack, "interior", "muro", "pared", "vinil", "acril")) {
                score += 4;
            }
            if (containsAny(haystack, "exterior", "fachada", "intemperie")) {
                score -= 2;
            }
        }
        if (containsAny(question, "fachada", "exterior", "patio")) {
            if (containsAny(haystack, "exterior", "fachada", "intemperie")) {
                score += 4;
            }
        }
        if (containsAny(question, "techo", "azotea", "humedad")) {
            if (containsAny(haystack, "imper", "techo", "azotea", "humedad")) {
                score += 4;
            }
        }
        if (containsAny(question, "herrer", "reja", "barandal", "metal")) {
            if (containsAny(haystack, "herrer", "metal", "esmalte", "anticorros")) {
                score += 4;
            }
        }
        return score;
    }

    private String buildRecommendations(List<KnowledgeSnippet> snippets) {
        return snippets.stream()
                .limit(3)
                .map(snippet -> {
                    String productName = snippet.metadata().getOrDefault("productName", snippet.title());
                    String price = snippet.metadata().getOrDefault("priceMxn", "sin precio");
                    String usage = snippet.metadata().getOrDefault("usage", snippet.excerpt());
                    return productName + " (" + price + "): " + usage;
                })
                .reduce((left, right) -> left + " | " + right)
                .orElse("Sin recomendaciones disponibles");
    }

    private boolean containsAny(String input, String... tokens) {
        for (String token : tokens) {
            if (input.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
