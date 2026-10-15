package com.agenthub.agent.domain.provider.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Standard streaming chunk contract across all LLM providers.
 * Supports content deltas and streaming tool call deltas.
 */
public class ChatChunk {

    private String id;
    private String deltaContent;
    private String finishReason;
    private TokenUsage usage;
    private List<ToolCall> toolCallsDelta = new ArrayList<>();

    public ChatChunk() {}

    public ChatChunk(String id, String deltaContent, String finishReason, TokenUsage usage) {
        this.id = id;
        this.deltaContent = deltaContent != null ? deltaContent : "";
        this.finishReason = finishReason;
        this.usage = usage;
    }

    public static ChatChunk of(String deltaContent) {
        return new ChatChunk(null, deltaContent, null, null);
    }

    public static ChatChunk of(String id, String deltaContent) {
        return new ChatChunk(id, deltaContent, null, null);
    }

    public static ChatChunk ofToolCalls(List<ToolCall> toolCalls) {
        ChatChunk chunk = new ChatChunk(null, "", null, null);
        chunk.setToolCallsDelta(toolCalls);
        return chunk;
    }

    public static ChatChunk finish(String finishReason, TokenUsage usage) {
        return new ChatChunk(null, "", finishReason, usage);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDeltaContent() { return deltaContent; }
    public void setDeltaContent(String deltaContent) { this.deltaContent = deltaContent; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    public TokenUsage getUsage() { return usage; }
    public void setUsage(TokenUsage usage) { this.usage = usage; }
    public List<ToolCall> getToolCallsDelta() { return toolCallsDelta; }
    public void setToolCallsDelta(List<ToolCall> toolCallsDelta) {
        this.toolCallsDelta = toolCallsDelta != null ? new ArrayList<>(toolCallsDelta) : new ArrayList<>();
    }
}
