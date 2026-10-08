package com.agenthub.team.domain.model;

/**
 * Functional roles assigned to members within a Multi-Agent Team.
 */
public enum TeamRole {
    /**
     * Overall orchestrator responsible for goal breakdown, delegation, and synthesis.
     */
    ORCHESTRATOR,

    /**
     * System & backend architect responsible for technical blueprint and domain design.
     */
    ARCHITECT,

    /**
     * Software engineer responsible for writing production and component code.
     */
    CODER,

    /**
     * Code reviewer responsible for inspecting code quality, standards, and safety.
     */
    REVIEWER,

    /**
     * Quality assurance and testing engineer responsible for test cases and verification.
     */
    TESTER,

    /**
     * Research & information specialist responsible for deep retrieval and domain facts.
     */
    RESEARCHER,

    /**
     * Custom user-defined specialist role.
     */
    CUSTOM
}
