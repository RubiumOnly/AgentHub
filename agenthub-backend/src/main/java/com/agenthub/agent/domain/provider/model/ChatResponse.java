package com.agenthub.agent.domain.provider.model;

/**
 * Standard unified chat response model across all LLM providers.
 */
public class ChatResponse {

    private String id;
    private String model;
    private String providerId;
    private String providerType;
    private String content;
    private String finishReason;
    private TokenUsage usage = TokenUsage.zero();
    private long latencyMs;
    private double estimatedCost;
    private boolean fallbackUsed = false;
    private String originalProviderId;

    public ChatResponse() {}

    public ChatResponse(String id,
                        String model,
                        String providerId,
                        String providerType,
                        String content,
                        String finishReason,
                        TokenUsage usage,
                        long latencyMs,
                        double estimatedCost) {
        this.id = id;
        this.model = model;
        this.providerId = providerId;
        this.providerType = providerType;
        this.content = content != null ? content : "";
        this.finishReason = finishReason != null ? finishReason : "stop";
        this.usage = usage != null ? usage : TokenUsage.zero();
        this.latencyMs = latencyMs;
        this.estimatedCost = estimatedCost;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }
    public String getProviderType() { return providerType; }
    public void setProviderType(String providerType) { this.providerType = providerType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFinishReason() { return finishReason; }
    public void setFinishReason(String finishReason) { this.finishReason = finishReason; }
    public TokenUsage getUsage() { return usage; }
    public void setUsage(TokenUsage usage) { this.usage = usage; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    public double getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
    public String getOriginalProviderId() { return originalProviderId; }
    public void setOriginalProviderId(String originalProviderId) { this.originalProviderId = originalProviderId; }
}
