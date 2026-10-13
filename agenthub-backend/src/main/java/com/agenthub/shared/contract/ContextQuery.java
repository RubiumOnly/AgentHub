package com.agenthub.shared.contract;

/**
 * Contract marker for read-only Queries within a BoundedContext.
 */
public interface ContextQuery {
    BoundedContext boundedContext();
}
