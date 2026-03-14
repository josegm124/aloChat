package com.alochat.ai.model;

public record StoreCatalogItem(
        String itemId,
        String productName,
        String category,
        String priceMxn,
        String unit,
        String usage
) {
}
