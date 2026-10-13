package com.agenthub.shared.contract;

import java.time.Instant;

/**
 * Contract marker for Domain Events emitted within a BoundedContext.
 */
public interface ContextDomainEvent {
    BoundedContext boundedContext();
    String eventType();
    Instant occurredAt();
}
