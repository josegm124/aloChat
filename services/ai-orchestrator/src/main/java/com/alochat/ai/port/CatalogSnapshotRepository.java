package com.alochat.ai.port;

import com.alochat.ai.model.StoreCatalogItem;
import java.util.List;
import java.util.Map;

public interface CatalogSnapshotRepository {

    Map<String, StoreCatalogItem> findByProductNames(String tenantId, List<String> productNames);
}
