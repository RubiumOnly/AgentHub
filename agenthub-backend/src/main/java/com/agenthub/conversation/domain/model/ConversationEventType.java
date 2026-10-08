package com.agenthub.conversation.domain.model;

/**
 * Event types dispatched over the Conversation Message Bus.
 */
public enum ConversationEventType {
    MESSAGE_POSTED,
    AGENT_TYPING,
    AGENT_REPLIED,
    HANDOFF_TRIGGERED,
    LOOP_DETECTED,
    SUMMARY_GENERATED,
    HEARTBEAT
}
