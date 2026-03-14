package com.alochat.ai.port;

import com.alochat.ai.model.AiContext;
import com.alochat.ai.model.AiGenerationResult;
import com.alochat.contracts.message.MessageEnvelope;

public interface AiResponseGenerator {

    AiGenerationResult generate(MessageEnvelope envelope, AiContext context);
}
