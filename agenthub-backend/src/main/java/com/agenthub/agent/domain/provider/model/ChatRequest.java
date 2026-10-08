package com.agenthub.agent.domain.provider.model;

import java.util.*;

/**
 * Standard unified chat request model for LLM invocations.
 */
public class ChatRequest {

    private String model;
    private List<ChatMessage> messages = new ArrayList<>();
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;
    private boolean stream = false;
    private Set<String> requiredCapabilities = new HashSet<>();
    private String preferredProvider;
    private int timeoutSeconds = 60;
    private Map<String, Object> metadata = new HashMap<>();

    public ChatRequest() {}

    public ChatRequest(String model, List<ChatMessage> messages) {
        this.model = model;
        if (messages != null) {
            this.messages.addAll(messages);
        }
    }

    public static ChatRequest of(String model, String userPrompt) {
        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(List.of(ChatMessage.user(userPrompt)));
        return req;
    }

    public static ChatRequest of(String model, String systemPrompt, String userPrompt) {
        ChatRequest req = new ChatRequest();
        req.setModel(model);
        req.setMessages(List.of(ChatMessage.system(systemPrompt), ChatMessage.user(userPrompt)));
        return req;
    }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<ChatMessage> getMessages() { return messages; }
    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages != null ? new ArrayList<>(messages) : new ArrayList<>();
    }
    public void addMessage(ChatMessage message) {
        if (message != null) {
            this.messages.add(message);
        }
    }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
    public Set<String> getRequiredCapabilities() { return requiredCapabilities; }
    public void setRequiredCapabilities(Set<String> requiredCapabilities) {
        this.requiredCapabilities = requiredCapabilities != null ? new HashSet<>(requiredCapabilities) : new HashSet<>();
    }
    public String getPreferredProvider() { return preferredProvider; }
    public void setPreferredProvider(String preferredProvider) { this.preferredProvider = preferredProvider; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
    }
}
