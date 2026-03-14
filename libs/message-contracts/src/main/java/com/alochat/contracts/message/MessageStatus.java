package com.alochat.contracts.message;

public enum MessageStatus {
    RECEIVED,
    NORMALIZED,
    ACCEPTED,
    PUBLISHED,
    PROCESSING,
    AI_PROCESSING,
    AI_COMPLETED,
    READY_FOR_DISPATCH,
    DISPATCHED,
    FAILED
}
