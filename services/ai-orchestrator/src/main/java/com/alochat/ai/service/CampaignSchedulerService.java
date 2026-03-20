package com.alochat.ai.service;

import com.alochat.ai.model.CampaignHint;
import com.alochat.ai.model.StoreCatalogItem;
import com.alochat.ai.port.CampaignHintRepository;
import com.alochat.ai.port.CatalogSnapshotRepository;
import com.alochat.ai.port.MessageAuditRepository;
import com.alochat.ai.port.OutboundMessagePublisher;
import com.alochat.contracts.message.Channel;
import com.alochat.contracts.message.ContentType;
import com.alochat.contracts.message.MessageEnvelope;
import com.alochat.contracts.message.MessageStatus;
import com.alochat.contracts.message.NormalizedContent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "alochat.campaigns.enabled", havingValue = "true", matchIfMissing = true)
public class CampaignSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(CampaignSchedulerService.class);

    private final CampaignHintRepository campaignHintRepository;
    private final CatalogSnapshotRepository catalogSnapshotRepository;
    private final MessageAuditRepository messageAuditRepository;
    private final OutboundMessagePublisher outboundMessagePublisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public CampaignSchedulerService(
            CampaignHintRepository campaignHintRepository,
            CatalogSnapshotRepository catalogSnapshotRepository,
            MessageAuditRepository messageAuditRepository,
            OutboundMessagePublisher outboundMessagePublisher
    ) {
        this.campaignHintRepository = campaignHintRepository;
        this.catalogSnapshotRepository = catalogSnapshotRepository;
        this.messageAuditRepository = messageAuditRepository;
        this.outboundMessagePublisher = outboundMessagePublisher;
    }

    @Scheduled(
            fixedDelayString = "${alochat.campaigns.poll-interval-ms:300000}",
            initialDelayString = "${alochat.campaigns.initial-delay-ms:60000}"
    )
    public void runCampaignSweep() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        try {
            Instant now = Instant.now();
            List<CampaignHint> candidates = campaignHintRepository.findCandidates(now, 25);
            for (CampaignHint candidate : candidates) {
                processCandidate(candidate, now);
            }
        } catch (Exception exception) {
            log.error("Campaign sweep failed", exception);
        } finally {
            running.set(false);
        }
    }

    private void processCandidate(CampaignHint hint, Instant now) {
        Map<String, StoreCatalogItem> currentProducts = catalogSnapshotRepository.findByProductNames(
                hint.tenantId(),
                hint.relatedProducts()
        );

        CampaignDispatch dispatch = switch (hint.hintType()) {
            case "REPLENISHMENT_FOLLOW_UP" -> buildReplenishmentDispatch(hint, currentProducts, now);
            case "DISCOUNT_WATCH" -> buildDiscountDispatch(hint, currentProducts, now);
            default -> null;
        };

        if (dispatch == null) {
            return;
        }

        messageAuditRepository.save(dispatch.envelope().withStatus(MessageStatus.AI_COMPLETED));
        MessageEnvelope outboundEnvelope = outboundMessagePublisher.publish(
                dispatch.envelope().withStatus(MessageStatus.READY_FOR_DISPATCH)
        );
        messageAuditRepository.save(outboundEnvelope);
        campaignHintRepository.markTriggered(hint.hintId(), now, dispatch.updatedPrices());
        log.info("Triggered campaign hintId={} channel={} userId={}",
                hint.hintId(), hint.channel(), hint.userId());
    }

    private CampaignDispatch buildReplenishmentDispatch(
            CampaignHint hint,
            Map<String, StoreCatalogItem> currentProducts,
            Instant now
    ) {
        if (hint.lastTriggeredAt() != null || hint.triggerAt() == null || hint.triggerAt().isAfter(now)) {
            return null;
        }

        String responseText;
        if (!currentProducts.isEmpty()) {
            StoreCatalogItem top = currentProducts.values().iterator().next();
            responseText = "Hace tiempo mostraste interes en un proyecto de pintura. "
                    + "Hoy sigue vigente en inventario " + top.productName()
                    + " con precio actual de " + top.priceMxn()
                    + ". Si quieres retomar el proyecto, te ayudo a cerrar la recomendacion.";
        } else {
            responseText = "Hace tiempo mostraste interes en un proyecto de pintura. "
                    + "Si quieres retomarlo, te puedo recomendar nuevamente las opciones actuales del inventario.";
        }

        return new CampaignDispatch(buildEnvelope(hint, responseText, now), extractPrices(currentProducts));
    }

    private CampaignDispatch buildDiscountDispatch(
            CampaignHint hint,
            Map<String, StoreCatalogItem> currentProducts,
            Instant now
    ) {
        if (hint.relatedProductPrices().isEmpty() || currentProducts.isEmpty()) {
            return null;
        }

        DiscountCandidate discountCandidate = null;
        for (Map.Entry<String, StoreCatalogItem> entry : currentProducts.entrySet()) {
            String previousPriceText = hint.relatedProductPrices().get(entry.getKey());
            if (previousPriceText == null || previousPriceText.isBlank()) {
                continue;
            }

            double previousPrice = parsePrice(previousPriceText);
            double currentPrice = parsePrice(entry.getValue().priceMxn());
            if (currentPrice <= 0 || previousPrice <= 0 || currentPrice >= previousPrice) {
                continue;
            }

            double delta = previousPrice - currentPrice;
            if (discountCandidate == null || delta > discountCandidate.discountValue()) {
                discountCandidate = new DiscountCandidate(entry.getValue(), previousPriceText, delta);
            }
        }

        if (discountCandidate == null) {
            return null;
        }

        String responseText = "El producto " + discountCandidate.item().productName()
                + " que te intereso antes ahora tiene mejor precio en inventario: antes "
                + discountCandidate.previousPrice()
                + " y hoy " + discountCandidate.item().priceMxn()
                + ". Si quieres, te ayudo a retomarlo.";
        return new CampaignDispatch(buildEnvelope(hint, responseText, now), extractPrices(currentProducts));
    }

    private MessageEnvelope buildEnvelope(CampaignHint hint, String responseText, Instant now) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("source", "campaign");
        metadata.put("campaignHintId", hint.hintId());
        metadata.put("campaignType", hint.hintType().toLowerCase(Locale.ROOT));

        return new MessageEnvelope(
                UUID.randomUUID().toString(),
                "campaign:" + hint.hintId() + ":" + now.getEpochSecond(),
                UUID.randomUUID().toString(),
                Channel.valueOf(hint.channel()),
                hint.tenantId(),
                hint.hintId(),
                hint.conversationId(),
                hint.userId(),
                now,
                new NormalizedContent(ContentType.TEXT, responseText),
                List.of(),
                Map.copyOf(metadata),
                null,
                MessageStatus.AI_COMPLETED
        );
    }

    private Map<String, String> extractPrices(Map<String, StoreCatalogItem> items) {
        Map<String, String> prices = new LinkedHashMap<>();
        items.values().forEach(item -> {
            if (item.productName() != null && !item.productName().isBlank()
                    && item.priceMxn() != null && !item.priceMxn().isBlank()) {
                prices.put(item.productName(), item.priceMxn());
            }
        });
        return Map.copyOf(prices);
    }

    private double parsePrice(String rawPrice) {
        try {
            return Double.parseDouble(rawPrice.replaceAll("[^\\d.]", ""));
        } catch (Exception exception) {
            return 0;
        }
    }

    private record CampaignDispatch(MessageEnvelope envelope, Map<String, String> updatedPrices) {
    }

    private record DiscountCandidate(StoreCatalogItem item, String previousPrice, double discountValue) {
    }
}
