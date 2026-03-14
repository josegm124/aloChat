package com.alochat.ai.port;

import com.alochat.ai.model.KnowledgeSnippet;
import com.alochat.contracts.message.MessageEnvelope;
import java.util.List;

public interface KnowledgeRetriever {

    List<KnowledgeSnippet> retrieve(MessageEnvelope envelope, int limit);
}
