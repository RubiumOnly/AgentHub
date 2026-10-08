package com.agenthub.conversation.domain.model;

/**
 * Coordination communication protocol pattern between collaborating agents.
 */
public enum MessageProtocolType {
    /**
     * Standard message without strict protocol requirements.
     */
    NORMAL,

    /**
     * Request expecting a correlated response from target recipient.
     */
    REQUEST_REPLY,

    /**
     * Contextual task handoff delegating responsibility to next agent.
     */
    HANDOFF,

    /**
     * Summary or synthesis finalizing conversation outcomes.
     */
    SUMMARIZE
}
