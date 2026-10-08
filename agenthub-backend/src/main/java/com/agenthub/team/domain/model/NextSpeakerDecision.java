package com.agenthub.team.domain.model;

/**
 * Output of a topology strategy deciding who speaks next and what collaborative action to take.
 */
public class NextSpeakerDecision {

    public enum Action {
        CONTINUE,
        HANDOFF,
        SUMMARIZE,
        TERMINATE
    }

    private String nextSpeakerId;
    private String nextSpeakerRole;
    private Action action;
    private String reason;
    private String promptGuidance;

    public NextSpeakerDecision() {}

    public NextSpeakerDecision(String nextSpeakerId, String nextSpeakerRole, Action action, String reason, String promptGuidance) {
        this.nextSpeakerId = nextSpeakerId;
        this.nextSpeakerRole = nextSpeakerRole;
        this.action = action;
        this.reason = reason;
        this.promptGuidance = promptGuidance;
    }

    public static NextSpeakerDecision continueWith(String speakerId, String role, String reason, String promptGuidance) {
        return new NextSpeakerDecision(speakerId, role, Action.CONTINUE, reason, promptGuidance);
    }

    public static NextSpeakerDecision handoffTo(String speakerId, String role, String reason, String promptGuidance) {
        return new NextSpeakerDecision(speakerId, role, Action.HANDOFF, reason, promptGuidance);
    }

    public static NextSpeakerDecision summarize(String speakerId, String role, String reason) {
        return new NextSpeakerDecision(speakerId, role, Action.SUMMARIZE, reason, "Please synthesize and summarize all team findings.");
    }

    public static NextSpeakerDecision terminate(String reason) {
        return new NextSpeakerDecision(null, null, Action.TERMINATE, reason, null);
    }

    public String getNextSpeakerId() { return nextSpeakerId; }
    public void setNextSpeakerId(String nextSpeakerId) { this.nextSpeakerId = nextSpeakerId; }
    public String getNextSpeakerRole() { return nextSpeakerRole; }
    public void setNextSpeakerRole(String nextSpeakerRole) { this.nextSpeakerRole = nextSpeakerRole; }
    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getPromptGuidance() { return promptGuidance; }
    public void setPromptGuidance(String promptGuidance) { this.promptGuidance = promptGuidance; }
}
