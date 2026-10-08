package com.agenthub.conversation.domain.model;

import java.util.Collections;
import java.util.List;

public class LoopDetectionResult {

    public enum LoopType {
        NONE,
        MAX_TURNS_EXCEEDED,
        DUPLICATE_CONTENT_COLLISION,
        OSCILLATION_DETECTED
    }

    private boolean loopDetected;
    private LoopType loopType;
    private String reason;
    private List<String> involvedAgents;

    public LoopDetectionResult() {
        this.loopDetected = false;
        this.loopType = LoopType.NONE;
        this.involvedAgents = Collections.emptyList();
    }

    public LoopDetectionResult(boolean loopDetected, LoopType loopType, String reason, List<String> involvedAgents) {
        this.loopDetected = loopDetected;
        this.loopType = loopType;
        this.reason = reason;
        this.involvedAgents = involvedAgents != null ? involvedAgents : Collections.emptyList();
    }

    public static LoopDetectionResult clean() {
        return new LoopDetectionResult(false, LoopType.NONE, "No loop detected", Collections.emptyList());
    }

    public static LoopDetectionResult maxTurnsExceeded(int maxTurns, List<String> agents) {
        return new LoopDetectionResult(
                true,
                LoopType.MAX_TURNS_EXCEEDED,
                "Exceeded maximum allowed coordination turns: " + maxTurns,
                agents
        );
    }

    public static LoopDetectionResult duplicateContent(String agentId, String snippet) {
        return new LoopDetectionResult(
                true,
                LoopType.DUPLICATE_CONTENT_COLLISION,
                "Duplicate message content collision detected from agent: " + agentId + " (snippet: '" + snippet + "')",
                List.of(agentId)
        );
    }

    public static LoopDetectionResult oscillation(String agentA, String agentB, String details) {
        return new LoopDetectionResult(
                true,
                LoopType.OSCILLATION_DETECTED,
                "Oscillating ping-pong loop detected between agents: " + agentA + " and " + agentB + ". " + details,
                List.of(agentA, agentB)
        );
    }

    public static LoopDetectionResult oscillation(List<String> agents, String details) {
        String agentsSummary = String.join(", ", agents);
        return new LoopDetectionResult(
                true,
                LoopType.OSCILLATION_DETECTED,
                "Oscillating loop detected among agents: [" + agentsSummary + "]. " + details,
                agents
        );
    }

    public boolean isLoopDetected() { return loopDetected; }
    public void setLoopDetected(boolean loopDetected) { this.loopDetected = loopDetected; }
    public LoopType getLoopType() { return loopType; }
    public void setLoopType(LoopType loopType) { this.loopType = loopType; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public List<String> getInvolvedAgents() { return involvedAgents; }
    public void setInvolvedAgents(List<String> involvedAgents) { this.involvedAgents = involvedAgents; }
}
