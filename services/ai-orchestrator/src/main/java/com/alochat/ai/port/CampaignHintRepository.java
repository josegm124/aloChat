package com.alochat.ai.port;

import com.alochat.ai.model.CampaignHint;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface CampaignHintRepository {

    void save(CampaignHint campaignHint);

    List<CampaignHint> findCandidates(Instant now, int limit);

    void markTriggered(String hintId, Instant triggeredAt, Map<String, String> relatedProductPrices);
}
