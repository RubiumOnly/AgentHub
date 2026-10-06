package com.agenthub.domain.conversation.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class InteractiveCard implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class SubtaskItem implements Serializable {
        private String id;
        private String targetAgent;
        private String title;
        private String status; // PENDING, RUNNING, COMPLETED, FAILED

        public SubtaskItem() {}

        public SubtaskItem(String id, String targetAgent, String title, String status) {
            this.id = id;
            this.targetAgent = targetAgent;
            this.title = title;
            this.status = status;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTargetAgent() { return targetAgent; }
        public void setTargetAgent(String targetAgent) { this.targetAgent = targetAgent; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    private String cardId;
    private String headerTitle;
    private String themeColor; // blue, green, orange, red
    private String summary;
    private List<SubtaskItem> subtasks = new ArrayList<>();
    private List<String> actions = new ArrayList<>();

    public InteractiveCard() {}

    public InteractiveCard(String cardId, String headerTitle, String summary) {
        this.cardId = cardId;
        this.headerTitle = headerTitle;
        this.summary = summary;
        this.themeColor = "blue";
    }

    public void addSubtask(String id, String targetAgent, String title, String status) {
        this.subtasks.add(new SubtaskItem(id, targetAgent, title, status));
    }

    public String getCardId() { return cardId; }
    public void setCardId(String cardId) { this.cardId = cardId; }
    public String getHeaderTitle() { return headerTitle; }
    public void setHeaderTitle(String headerTitle) { this.headerTitle = headerTitle; }
    public String getThemeColor() { return themeColor; }
    public void setThemeColor(String themeColor) { this.themeColor = themeColor; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<SubtaskItem> getSubtasks() { return subtasks; }
    public void setSubtasks(List<SubtaskItem> subtasks) { this.subtasks = subtasks; }
    public List<String> getActions() { return actions; }
    public void setActions(List<String> actions) { this.actions = actions; }
}
