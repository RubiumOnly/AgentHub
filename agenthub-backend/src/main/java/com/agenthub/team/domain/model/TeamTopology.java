package com.agenthub.team.domain.model;

/**
 * Coordination topology defining communication and delegation patterns in a Multi-Agent Team.
 */
public enum TeamTopology {
    /**
     * Master-Worker hierarchical topology.
     * Central Leader coordinates task decomposition, dispatches to workers, and synthesizes output.
     */
    HIERARCHICAL,

    /**
     * Peer-to-Peer collaborative topology.
     * Members negotiate directly without a central master, supporting direct handoffs and consensus.
     */
    PEER_TO_PEER,

    /**
     * Round-Robin rotation topology.
     * Members take turns speaking/acting strictly according to member sort order.
     */
    ROUND_ROBIN
}
