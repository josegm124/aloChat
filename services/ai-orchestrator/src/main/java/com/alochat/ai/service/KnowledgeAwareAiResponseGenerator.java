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
        ReplyLanguage replyLanguage = resolveReplyLanguage(envelope);
        String question = safeText(envelope.content().text()).toLowerCase(Locale.ROOT);
        RecommendationIntent recommendationIntent = detectIntent(question);
        boolean asksPromotion = isPromotionIntent(question);
        List<KnowledgeSnippet> rankedSnippets = rankSnippets(question, replyLanguage, recommendationIntent, asksPromotion, snippets);
        if (rankedSnippets.isEmpty()) {
            return replyLanguage == ReplyLanguage.EN
                    ? "I did not find a clear match in the current inventory. If you tell me whether you need interior, exterior, metalwork, roof, or a specific product, I can give you a more precise recommendation."
                    : "No encontre una coincidencia clara en el inventario actual. "
                    + "Si me dices si buscas interior, exterior, herreria, techo o un producto especifico, te respondo con una recomendacion mas precisa.";
        }

        KnowledgeSnippet top = rankedSnippets.getFirst();
        String productName = top.metadata().getOrDefault("productName", top.title());
        String price = effectivePrice(top, replyLanguage);
        String unit = top.metadata().getOrDefault("unit", "");
        String usage = top.metadata().getOrDefault("usage", top.excerpt());
        boolean asksPrice = containsAny(question, "cuanto cuesta", "precio", "costo", "how much", "price", "cost");
        boolean asksAvailability = containsAny(question, "tienes", "tiene", "hay", "disponible", "manejan",
                "do you have", "available", "in stock", "carry");
        boolean asksDelivery = containsAny(question, "envio", "entrega", "cdmx", "edomex", "foraneo",
                "delivery", "shipping", "ship");
        boolean asksDiscount = containsAny(question, "descuento", "mayoreo", "inversionista",
                "discount", "wholesale", "bulk");
        boolean asksRecommendation = recommendationIntent != RecommendationIntent.DIRECT_PRODUCT;

        StringBuilder response = new StringBuilder();
        if (asksAvailability || asksPrice) {
            if (replyLanguage == ReplyLanguage.EN) {
                response.append("Yes, ")
                        .append(productName)
                        .append(" appears in the 2026 inventory. ");
            } else {
                response.append("Si, en el inventario 2026 aparece ")
                        .append(productName)
                        .append(". ");
            }
            appendPriceBlock(response, replyLanguage, top, price);
            if (!unit.isBlank()) {
                response.append(replyLanguage == ReplyLanguage.EN ? " per " : " por ").append(unit);
            }
            response.append(replyLanguage == ReplyLanguage.EN ? ". Recommended use: " : ". Uso recomendado: ")
                    .append(usage)
                    .append(".");
            appendOfferValidity(response, replyLanguage, top);
        } else if (asksPromotion && isActiveOffer(top)) {
            if (replyLanguage == ReplyLanguage.EN) {
                response.append(productName).append(" currently has an active promotion. ");
            } else {
                response.append(productName).append(" tiene una promocion activa. ");
            }
            appendPriceBlock(response, replyLanguage, top, price);
            if (!unit.isBlank()) {
                response.append(replyLanguage == ReplyLanguage.EN ? " per " : " por ").append(unit);
            }
            response.append(replyLanguage == ReplyLanguage.EN ? ". " : ". ");
            appendOfferValidity(response, replyLanguage, top);
            response.append(replyLanguage == ReplyLanguage.EN ? "Recommended use: " : "Uso recomendado: ")
                    .append(usage)
                    .append(".");
        } else if (asksRecommendation) {
            response.append(buildRecommendationResponse(replyLanguage, recommendationIntent, rankedSnippets));
        } else {
            response.append(replyLanguage == ReplyLanguage.EN
                            ? "Based on your question, the most relevant item in the inventory is "
                            : "Con base en tu consulta, lo mas relevante del inventario es ")
                    .append(productName)
                    .append(" (")
                    .append(price);
            if (!unit.isBlank()) {
                response.append(replyLanguage == ReplyLanguage.EN ? " per " : " por ").append(unit);
            }
            response.append(replyLanguage == ReplyLanguage.EN ? "). Recommended use: " : "). Uso recomendado: ")
                    .append(usage)
                    .append(".");
            appendOfferValidity(response, replyLanguage, top);
        }

        if (asksDelivery) {
            response.append(replyLanguage == ReplyLanguage.EN
                    ? " Delivery policy: CDMX/EdoMex 24h, nationwide 48-96h, free shipping above $1,500 MXN and $250 MXN shipping for retail orders."
                    : " Politicas de entrega: CDMX/EdoMex 24h, foraneo 48-96h, envio gratis arriba de $1,500 MXN y $250 MXN en menudeo.");
        }
        if (asksDiscount) {
            response.append(replyLanguage == ReplyLanguage.EN
                    ? " For purchases above $500k MXN, a 12% investor discount applies."
                    : " Para compras mayores a $500k MXN aplica descuento de inversionista del 12%.");
        }
        if (conversationMemory != null && conversationMemory.summary() != null && !conversationMemory.summary().isBlank()) {
            response.append(replyLanguage == ReplyLanguage.EN
                    ? " I also have prior context from this conversation to keep recommending related products."
                    : " Tambien tengo contexto previo de esta conversacion para seguir recomendando productos relacionados.");
        }
        return response.toString();
    }

    private List<KnowledgeSnippet> rankSnippets(
            String question,
            ReplyLanguage replyLanguage,
            RecommendationIntent recommendationIntent,
            boolean asksPromotion,
            List<KnowledgeSnippet> snippets
    ) {
        return snippets.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeSnippet snippet) ->
                                rankingScore(question, replyLanguage, recommendationIntent, asksPromotion, snippet))
                        .reversed()
                        .thenComparing(Comparator.comparingDouble(KnowledgeSnippet::score).reversed()))
                .filter(snippet -> rankingScore(question, replyLanguage, recommendationIntent, asksPromotion, snippet) > -3)
                .toList();
    }

    private int rankingScore(
            String question,
            ReplyLanguage replyLanguage,
            RecommendationIntent recommendationIntent,
            boolean asksPromotion,
            KnowledgeSnippet snippet
    ) {
        int score = intentScore(question, recommendationIntent, snippet);
        score += languageScore(replyLanguage, snippet);
        score += offerScore(asksPromotion, snippet);
        return score;
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

    private int languageScore(ReplyLanguage replyLanguage, KnowledgeSnippet snippet) {
        String language = safeText(snippet.metadata().get("language")).toLowerCase(Locale.ROOT);
        if (replyLanguage == ReplyLanguage.EN) {
            return language.equals("en") ? 4 : 0;
        }
        return language.equals("es") || language.isBlank() ? 4 : 0;
    }

    private int offerScore(boolean asksPromotion, KnowledgeSnippet snippet) {
        boolean offer = isOfferSnippet(snippet);
        boolean active = isActiveOffer(snippet);
        if (asksPromotion) {
            int score = 0;
            if (offer) {
                score += 6;
            }
            if (active) {
                score += 8;
            }
            return score;
        }
        if (offer && active) {
            return 1;
        }
        return 0;
    }

    private String buildRecommendationResponse(
            ReplyLanguage replyLanguage,
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
        response.append(introFor(replyLanguage, recommendationIntent)).append(" ");
        response.append(buildRecommendationList(replyLanguage, mainProducts));
        if (preparation != null) {
            response.append(replyLanguage == ReplyLanguage.EN
                            ? " If the surface is new or porous, use "
                            : " Si la superficie esta nueva o porosa, primero usa ")
                    .append(productName(preparation))
                    .append(" (")
                    .append(price(preparation))
                    .append(replyLanguage == ReplyLanguage.EN
                            ? ") first to prepare the wall."
                            : ") para preparar el muro.");
        }
        response.append(replyLanguage == ReplyLanguage.EN
                ? " If you tell me whether it is for interior or exterior use, I can narrow it down to a single inventory option."
                : " Si me dices si es interior o exterior, te cierro la recomendacion a una sola opcion del inventario.");
        return response.toString();
    }

    private String buildRecommendationList(ReplyLanguage replyLanguage, List<KnowledgeSnippet> snippets) {
        Set<String> seenProducts = new HashSet<>();
        List<String> parts = new ArrayList<>();
        for (KnowledgeSnippet snippet : snippets) {
            String productName = productName(snippet);
            if (!seenProducts.add(productName)) {
                continue;
            }
            parts.add(productName + " (" + effectivePrice(snippet, replyLanguage) + "): "
                    + usage(snippet)
                    + offerSuffix(replyLanguage, snippet));
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

    private String effectivePrice(KnowledgeSnippet snippet, ReplyLanguage replyLanguage) {
        if (isActiveOffer(snippet)) {
            String promoPrice = safeText(snippet.metadata().get("promoPriceMxn"));
            if (!promoPrice.isBlank()) {
                return promoPrice;
            }
        }
        String price = safeText(snippet.metadata().get("priceMxn"));
        if (!price.isBlank()) {
            return price;
        }
        String regularPrice = safeText(snippet.metadata().get("regularPriceMxn"));
        if (!regularPrice.isBlank()) {
            return regularPrice;
        }
        return replyLanguage == ReplyLanguage.EN ? "price unavailable" : "sin precio";
    }

    private boolean isOfferSnippet(KnowledgeSnippet snippet) {
        return "offer".equalsIgnoreCase(safeText(snippet.metadata().get("docType")));
    }

    private boolean isActiveOffer(KnowledgeSnippet snippet) {
        return "true".equalsIgnoreCase(safeText(snippet.metadata().get("offerActive")));
    }

    private boolean isPromotionIntent(String question) {
        return containsAny(question, "descuento", "promocion", "oferta", "rebaja",
                "discount", "promotion", "offer", "sale", "deal");
    }

    private void appendPriceBlock(StringBuilder response, ReplyLanguage replyLanguage, KnowledgeSnippet snippet, String effectivePrice) {
        String regularPrice = safeText(snippet.metadata().get("regularPriceMxn"));
        String promoPrice = safeText(snippet.metadata().get("promoPriceMxn"));
        if (isActiveOffer(snippet) && !promoPrice.isBlank()) {
            if (replyLanguage == ReplyLanguage.EN) {
                response.append("Promotional price: ").append(promoPrice);
                if (!regularPrice.isBlank()) {
                    response.append(" (regular price: ").append(regularPrice).append(")");
                }
            } else {
                response.append("Precio promocional: ").append(promoPrice);
                if (!regularPrice.isBlank()) {
                    response.append(" (precio regular: ").append(regularPrice).append(")");
                }
            }
            return;
        }
        response.append(replyLanguage == ReplyLanguage.EN ? "Price: " : "Precio: ").append(effectivePrice);
    }

    private void appendOfferValidity(StringBuilder response, ReplyLanguage replyLanguage, KnowledgeSnippet snippet) {
        if (!isActiveOffer(snippet)) {
            return;
        }
        String validUntil = safeText(snippet.metadata().get("validUntil"));
        if (validUntil.isBlank()) {
            return;
        }
        response.append(replyLanguage == ReplyLanguage.EN
                ? " Offer valid until "
                : " Oferta vigente hasta ")
                .append(validUntil)
                .append(".");
    }

    private String offerSuffix(ReplyLanguage replyLanguage, KnowledgeSnippet snippet) {
        if (!isActiveOffer(snippet)) {
            return "";
        }
        String promoPrice = safeText(snippet.metadata().get("promoPriceMxn"));
        String regularPrice = safeText(snippet.metadata().get("regularPriceMxn"));
        if (promoPrice.isBlank()) {
            return replyLanguage == ReplyLanguage.EN ? " [active offer]" : " [oferta activa]";
        }
        if (replyLanguage == ReplyLanguage.EN) {
            return regularPrice.isBlank()
                    ? " [offer]"
                    : " [offer from " + regularPrice + " to " + promoPrice + "]";
        }
        return regularPrice.isBlank()
                ? " [oferta]"
                : " [oferta de " + regularPrice + " a " + promoPrice + "]";
    }

    private String introFor(ReplyLanguage replyLanguage, RecommendationIntent recommendationIntent) {
        if (replyLanguage == ReplyLanguage.EN) {
            return switch (recommendationIntent) {
                case INTERIOR_WALL -> "For an interior wall, these are the inventory options that fit best:";
                case EXTERIOR_WALL -> "For an exterior wall or facade, these are the inventory options that fit best:";
                case ROOF_OR_HUMIDITY -> "For roof, rooftop, or humidity issues, these are the closest inventory matches:";
                case METAL -> "For metalwork, gates, or steel, these are the inventory options:";
                case WOOD -> "For wood, these are the inventory options:";
                case BATHROOM_OR_KITCHEN -> "For bathroom, kitchen, or similar surfaces, these are the inventory options:";
                case SPECIAL_SURFACE -> "For that surface, this is the closest match I have in the inventory:";
                case DIRECT_PRODUCT -> "This is the most relevant inventory result:";
            };
        }
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
                "para pintar", "recomienda", "recomendacion", "what kind", "which one", "recommend", "i want to paint",
                "paint for", "tell me a paint", "what paint should i use")) {
            if (containsAny(question, "techo", "azotea", "humedad", "goteras", "imperme")) {
                return RecommendationIntent.ROOF_OR_HUMIDITY;
            }
            if (containsAny(question, "techo", "azotea", "humedad", "goteras", "imperme", "roof", "rooftop", "humidity", "leak")) {
                return RecommendationIntent.ROOF_OR_HUMIDITY;
            }
            if (containsAny(question, "herrer", "reja", "barandal", "metal", "acero", "metal", "gate", "railing", "steel")) {
                return RecommendationIntent.METAL;
            }
            if (containsAny(question, "madera", "barniz", "puerta", "deck", "wood", "varnish", "door")) {
                return RecommendationIntent.WOOD;
            }
            if (containsAny(question, "bano", "baño", "cocina", "azulejo", "bathroom", "kitchen", "tile")) {
                return RecommendationIntent.BATHROOM_OR_KITCHEN;
            }
            if (containsAny(question, "fachada", "exterior", "patio", "intemperie", "facade", "outdoor", "outside", "weather")) {
                return RecommendationIntent.EXTERIOR_WALL;
            }
            if (containsAny(question, "pared", "muro", "interior", "cuarto", "recamara", "sala", "comedor", "casa",
                    "wall", "room", "bedroom", "living room", "dining room", "house", "interior")) {
                return RecommendationIntent.INTERIOR_WALL;
            }
            return RecommendationIntent.SPECIAL_SURFACE;
        }

        if (containsAny(question, "pared", "muro", "interior", "cuarto", "recamara", "sala", "comedor",
                "wall", "interior", "room", "bedroom", "living room", "dining room")) {
            return RecommendationIntent.INTERIOR_WALL;
        }
        if (containsAny(question, "fachada", "exterior", "patio", "intemperie", "facade", "exterior", "outdoor", "outside")) {
            return RecommendationIntent.EXTERIOR_WALL;
        }
        if (containsAny(question, "techo", "azotea", "humedad", "imperme", "roof", "rooftop", "humidity", "waterproof")) {
            return RecommendationIntent.ROOF_OR_HUMIDITY;
        }
        if (containsAny(question, "herrer", "reja", "barandal", "metal", "acero", "metal", "gate", "railing", "steel")) {
            return RecommendationIntent.METAL;
        }
        if (containsAny(question, "madera", "barniz", "puerta", "deck", "wood", "varnish", "door")) {
            return RecommendationIntent.WOOD;
        }
        return RecommendationIntent.DIRECT_PRODUCT;
    }

    private ReplyLanguage resolveReplyLanguage(MessageEnvelope envelope) {
        String preferredLanguage = safeText(envelope.metadata().get("preferredLanguage")).toLowerCase(Locale.ROOT);
        if (preferredLanguage.startsWith("en")) {
            return ReplyLanguage.EN;
        }
        if (preferredLanguage.startsWith("es")) {
            return ReplyLanguage.ES;
        }

        String locale = safeText(envelope.metadata().get("locale")).toLowerCase(Locale.ROOT);
        if (locale.startsWith("en")) {
            return ReplyLanguage.EN;
        }
        if (locale.startsWith("es")) {
            return ReplyLanguage.ES;
        }

        String question = safeText(envelope.content().text()).toLowerCase(Locale.ROOT);
        if (containsAny(question, "how much", "price", "recommend", "paint", "wall", "interior", "exterior", "roof", "wood")) {
            return ReplyLanguage.EN;
        }
        return ReplyLanguage.ES;
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

    private enum ReplyLanguage {
        ES,
        EN
    }
}
