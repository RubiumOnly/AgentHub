package com.agenthub.team.dto;

import com.agenthub.team.domain.model.NextSpeakerDecision;

public class TeamCoordinateResultView {
    private String nextSpeakerId;
    private String nextSpeakerRole;
    private NextSpeakerDecision.Action action;
    private String reason;
    private String promptGuidance;

    public TeamCoordinateResultView() {}

    public TeamCoordinateResultView(String nextSpeakerId, String nextSpeakerRole,
                                    NextSpeakerDecision.Action action, String reason, String promptGuidance) {
        this.nextSpeakerId = nextSpeakerId;
        this.nextSpeakerRole = nextSpeakerRole;
        this.action = action;
        this.reason = reason;
        this.promptGuidance = promptGuidance;
    }

    public static TeamCoordinateResultView fromDecision(NextSpeakerDecision decision) {
        return new TeamCoordinateResultView(
                decision.getNextSpeakerId(),
                decision.getNextSpeakerRole(),
                decision.getAction(),
                decision.getReason(),
                decision.getPromptGuidance()
        );
    }

    public String getNextSpeakerId() { return nextSpeakerId; }
    public void setNextSpeakerId(String nextSpeakerId) { this.nextSpeakerId = nextSpeakerId; }
    public String getNextSpeakerRole() { return nextSpeakerRole; }
    public void setNextSpeakerRole(String nextSpeakerRole) { this.nextSpeakerRole = nextSpeakerRole; }
    public NextSpeakerDecision.Action getAction() { return action; }
    public void setAction(NextSpeakerDecision.Action action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getPromptGuidance() { return promptGuidance; }
    public void setPromptGuidance(String promptGuidance) { this.promptGuidance = promptGuidance; }
}
