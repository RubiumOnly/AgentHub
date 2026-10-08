package com.agenthub.agent.domain.provider.model;

import java.util.Objects;

/**
 * Standard unified chat message abstraction across all LLM providers (OpenAI, Anthropic, Gemini, Ollama).
 */
public class ChatMessage {

    private String role; // system, user, assistant, tool
    private String content;
    private String name;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role = role != null ? role.toLowerCase() : "user";
        this.content = content != null ? content : "";
    }

    public ChatMessage(String role, String content, String name) {
        this.role = role != null ? role.toLowerCase() : "user";
        this.content = content != null ? content : "";
        this.name = name;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    public static ChatMessage tool(String name, String content) {
        return new ChatMessage("tool", content, name);
    }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatMessage that)) return false;
        return Objects.equals(role, that.role) && Objects.equals(content, that.content) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content, name);
    }

    @Override
    public String toString() {
        return "ChatMessage{" + "role='" + role + '\'' + ", contentLength=" + (content != null ? content.length() : 0) + '}';
    }
}
