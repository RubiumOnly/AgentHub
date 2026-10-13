package com.agenthub.shared.contract;

/**
 * Contract marker for state-mutating Commands within a BoundedContext.
 */
public interface ContextCommand {
    BoundedContext boundedContext();
}
