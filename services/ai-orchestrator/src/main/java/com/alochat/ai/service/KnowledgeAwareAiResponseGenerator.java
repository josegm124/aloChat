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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Comparator;
import java.util.Set;
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
        CampaignHint campaignHint = conversationMemoryService.createHint(envelope, updatedMemory, snippets);

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
        RecommendationIntent recommendationIntent = detectIntent(question);
        List<KnowledgeSnippet> rankedSnippets = rankSnippets(question, recommendationIntent, snippets);
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
        boolean asksRecommendation = recommendationIntent != RecommendationIntent.DIRECT_PRODUCT;

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
            response.append(buildRecommendationResponse(recommendationIntent, rankedSnippets));
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

    private List<KnowledgeSnippet> rankSnippets(
            String question,
            RecommendationIntent recommendationIntent,
            List<KnowledgeSnippet> snippets
    ) {
        return snippets.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeSnippet snippet) -> intentScore(question, recommendationIntent, snippet))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeSnippet::score).reversed()))
                .filter(snippet -> intentScore(question, recommendationIntent, snippet) > -3)
                .toList();
    }

    private int intentScore(String question, RecommendationIntent recommendationIntent, KnowledgeSnippet snippet) {
        String haystack = (
                safeText(snippet.title()) + " "
                        + safeText(snippet.excerpt()) + " "
                        + safeText(snippet.metadata().get("category"))
        ).toLowerCase(Locale.ROOT);

        int score = switch (recommendationIntent) {
            case INTERIOR_WALL -> scoreInteriorWall(haystack);
            case EXTERIOR_WALL -> scoreExteriorWall(haystack);
            case ROOF_OR_HUMIDITY -> scoreRoofOrHumidity(haystack);
            case METAL -> scoreMetal(haystack);
            case WOOD -> scoreWood(haystack);
            case BATHROOM_OR_KITCHEN -> scoreBathroomOrKitchen(haystack);
            case SPECIAL_SURFACE -> scoreSpecialSurface(haystack);
            case DIRECT_PRODUCT -> 0;
        };

        if (containsAny(question, "precio", "cuanto", "costo", "tienes", "disponible")
                && containsAny(haystack, safeText(snippet.title()).toLowerCase(Locale.ROOT))) {
            score += 1;
        }
        return score;
    }

    private String buildRecommendationResponse(
            RecommendationIntent recommendationIntent,
            List<KnowledgeSnippet> rankedSnippets
    ) {
        List<KnowledgeSnippet> mainProducts = rankedSnippets.stream()
                .filter(snippet -> !isPreparationProduct(snippet))
                .limit(2)
                .toList();
        KnowledgeSnippet preparation = rankedSnippets.stream()
                .filter(this::isPreparationProduct)
                .findFirst()
                .orElse(null);

        if (mainProducts.isEmpty()) {
            mainProducts = rankedSnippets.stream().limit(2).toList();
        }

        StringBuilder response = new StringBuilder();
        response.append(introFor(recommendationIntent)).append(" ");
        response.append(buildRecommendationList(mainProducts));
        if (preparation != null) {
            response.append(" Si la superficie esta nueva o porosa, primero usa ")
                    .append(productName(preparation))
                    .append(" (")
                    .append(price(preparation))
                    .append(") para preparar el muro.");
        }
        response.append(" Si me dices si es interior o exterior, te cierro la recomendacion a una sola opcion del inventario.");
        return response.toString();
    }

    private String buildRecommendationList(List<KnowledgeSnippet> snippets) {
        Set<String> seenProducts = new HashSet<>();
        List<String> parts = new ArrayList<>();
        for (KnowledgeSnippet snippet : snippets) {
            String productName = productName(snippet);
            if (!seenProducts.add(productName)) {
                continue;
            }
            parts.add(productName + " (" + price(snippet) + "): " + usage(snippet));
            if (parts.size() == 2) {
                break;
            }
        }
        return String.join(" | ", parts);
    }

    private String productName(KnowledgeSnippet snippet) {
        return snippet.metadata().getOrDefault("productName", snippet.title());
    }

    private String price(KnowledgeSnippet snippet) {
        return snippet.metadata().getOrDefault("priceMxn", "sin precio");
    }

    private String usage(KnowledgeSnippet snippet) {
        return snippet.metadata().getOrDefault("usage", snippet.excerpt());
    }

    private String introFor(RecommendationIntent recommendationIntent) {
        return switch (recommendationIntent) {
            case INTERIOR_WALL -> "Para pared o muro interior, estas son las opciones del inventario que mejor encajan:";
            case EXTERIOR_WALL -> "Para fachada o muro exterior, estas son las opciones del inventario que mejor encajan:";
            case ROOF_OR_HUMIDITY -> "Para techo, azotea o humedad, estas son las opciones mas alineadas del inventario:";
            case METAL -> "Para herreria, rejas o metal, estas son las opciones del inventario:";
            case WOOD -> "Para madera, estas son las opciones del inventario:";
            case BATHROOM_OR_KITCHEN -> "Para bano, cocina o superficies similares, estas son las opciones del inventario:";
            case SPECIAL_SURFACE -> "Para esa superficie, esto es lo mas cercano que tengo en el inventario:";
            case DIRECT_PRODUCT -> "Esto es lo mas relevante del inventario:";
        };
    }

    private boolean isPreparationProduct(KnowledgeSnippet snippet) {
        String haystack = (productName(snippet) + " " + usage(snippet) + " "
                + safeText(snippet.metadata().get("category"))).toLowerCase(Locale.ROOT);
        return containsAny(haystack, "sellador", "preparacion", "primario", "capa base");
    }

    private RecommendationIntent detectIntent(String question) {
        if (containsAny(question, "que tipo", "cual usar", "me recomiendas", "quiero pintar", "dime una pintura", "dime que pintura",
                "para pintar", "recomienda", "recomendacion")) {
            if (containsAny(question, "techo", "azotea", "humedad", "goteras", "imperme")) {
                return RecommendationIntent.ROOF_OR_HUMIDITY;
            }
            if (containsAny(question, "herrer", "reja", "barandal", "metal", "acero")) {
                return RecommendationIntent.METAL;
            }
            if (containsAny(question, "madera", "barniz", "puerta", "deck")) {
                return RecommendationIntent.WOOD;
            }
            if (containsAny(question, "bano", "baño", "cocina", "azulejo")) {
                return RecommendationIntent.BATHROOM_OR_KITCHEN;
            }
            if (containsAny(question, "fachada", "exterior", "patio", "intemperie")) {
                return RecommendationIntent.EXTERIOR_WALL;
            }
            if (containsAny(question, "pared", "muro", "interior", "cuarto", "recamara", "sala", "comedor", "casa")) {
                return RecommendationIntent.INTERIOR_WALL;
            }
            return RecommendationIntent.SPECIAL_SURFACE;
        }

        if (containsAny(question, "pared", "muro", "interior", "cuarto", "recamara", "sala", "comedor")) {
            return RecommendationIntent.INTERIOR_WALL;
        }
        if (containsAny(question, "fachada", "exterior", "patio", "intemperie")) {
            return RecommendationIntent.EXTERIOR_WALL;
        }
        if (containsAny(question, "techo", "azotea", "humedad", "imperme")) {
            return RecommendationIntent.ROOF_OR_HUMIDITY;
        }
        if (containsAny(question, "herrer", "reja", "barandal", "metal", "acero")) {
            return RecommendationIntent.METAL;
        }
        if (containsAny(question, "madera", "barniz", "puerta", "deck")) {
            return RecommendationIntent.WOOD;
        }
        return RecommendationIntent.DIRECT_PRODUCT;
    }

    private int scoreInteriorWall(String haystack) {
        int score = 0;
        if (containsAny(haystack, "interior", "muro", "pared", "vinil", "acril", "habitacion")) {
            score += 6;
        }
        if (containsAny(haystack, "premium interior", "vinil-acrilica premium", "vinil-acrilica estandar", "muros interiores")) {
            score += 4;
        }
        if (containsAny(haystack, "sellador", "preparar paredes", "concreto nuevos", "salitre")) {
            score += 3;
        }
        if (containsAny(haystack, "teja", "asfalto", "alberca", "barcos", "aviones", "herrer", "metal", "azotea", "techo")) {
            score -= 6;
        }
        return score;
    }

    private int scoreExteriorWall(String haystack) {
        int score = 0;
        if (containsAny(haystack, "exterior", "fachada", "intemperie", "muros exteriores")) {
            score += 6;
        }
        if (containsAny(haystack, "sellador", "concreto nuevos", "salitre", "texturizado")) {
            score += 2;
        }
        if (containsAny(haystack, "teja", "alberca", "metal", "asadores", "azotea", "techo")) {
            score -= 4;
        }
        return score;
    }

    private int scoreRoofOrHumidity(String haystack) {
        int score = 0;
        if (containsAny(haystack, "imper", "techo", "azotea", "humedad", "filtraciones", "termorreflectante")) {
            score += 6;
        }
        if (containsAny(haystack, "pared", "interior", "herrer", "metal", "teja")) {
            score -= 4;
        }
        return score;
    }

    private int scoreMetal(String haystack) {
        int score = 0;
        if (containsAny(haystack, "herrer", "metal", "acero", "anticorros", "esmalte")) {
            score += 6;
        }
        if (containsAny(haystack, "interior", "fachada", "techo", "madera")) {
            score -= 4;
        }
        return score;
    }

    private int scoreWood(String haystack) {
        int score = 0;
        if (containsAny(haystack, "madera", "barniz", "deck", "puertas de madera")) {
            score += 6;
        }
        if (containsAny(haystack, "metal", "herrer", "azotea", "alberca")) {
            score -= 4;
        }
        return score;
    }

    private int scoreBathroomOrKitchen(String haystack) {
        int score = 0;
        if (containsAny(haystack, "azulejos", "banos", "cocinas", "epoxico", "grado alimenticio", "sanitario")) {
            score += 6;
        }
        if (containsAny(haystack, "herrer", "techo", "madera")) {
            score -= 4;
        }
        return score;
    }

    private int scoreSpecialSurface(String haystack) {
        if (containsAny(haystack, "barcos", "aviones", "quirofanos", "electrodomesticos", "canchas", "albercas")) {
            return 1;
        }
        return 0;
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

    private enum RecommendationIntent {
        DIRECT_PRODUCT,
        INTERIOR_WALL,
        EXTERIOR_WALL,
        ROOF_OR_HUMIDITY,
        METAL,
        WOOD,
        BATHROOM_OR_KITCHEN,
        SPECIAL_SURFACE
    }
}
