package com.agenthub.agent.domain.provider.model;

import java.util.Objects;

/**
 * Standard token usage metrics model.
 */
public class TokenUsage {

    private int promptTokens;
    private int completionTokens;
    private int totalTokens;

    public TokenUsage() {}

    public TokenUsage(int promptTokens, int completionTokens) {
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = this.promptTokens + this.completionTokens;
    }

    public TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.totalTokens = totalTokens > 0 ? totalTokens : (this.promptTokens + this.completionTokens);
    }

    public static TokenUsage of(int promptTokens, int completionTokens) {
        return new TokenUsage(promptTokens, completionTokens);
    }

    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenUsage that)) return false;
        return promptTokens == that.promptTokens && completionTokens == that.completionTokens && totalTokens == that.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "TokenUsage{prompt=" + promptTokens + ", completion=" + completionTokens + ", total=" + totalTokens + '}';
    }
}
