package com.agenthub.conversation.domain.model;

/**
 * Routing visibility category for conversation messages.
 */
public enum MessageType {
    /**
     * Broadcast message visible to all conversation participants.
     */
    BROADCAST,

    /**
     * Point-to-point private message visible only to sender and recipient (and admin/orchestrator).
     */
    DIRECT,

    /**
     * System event/notification message broadcast to all participants.
     */
    SYSTEM
}
