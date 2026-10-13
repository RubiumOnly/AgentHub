package com.agenthub.agent.domain.context;

import com.agenthub.project.domain.context.SecurityLevel;

public class ContextLayer {

    private ContextLayerType layerType;
    private String content;
    private int tokenBudget;
    private int actualTokens;
    private boolean truncated;
    private ContextTruncationStrategy truncationStrategy;
    private SecurityLevel securityLevel;
    private String sourceRef;

    public ContextLayer() {}

    public ContextLayer(ContextLayerType layerType,
                        String content,
                        int tokenBudget,
                        int actualTokens,
                        boolean truncated,
                        ContextTruncationStrategy truncationStrategy,
                        SecurityLevel securityLevel,
                        String sourceRef) {
        this.layerType = layerType;
        this.content = content;
        this.tokenBudget = tokenBudget;
        this.actualTokens = actualTokens;
        this.truncated = truncated;
        this.truncationStrategy = truncationStrategy;
        this.securityLevel = securityLevel;
        this.sourceRef = sourceRef;
    }

    public ContextLayerType getLayerType() { return layerType; }
    public void setLayerType(ContextLayerType layerType) { this.layerType = layerType; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int tokenBudget) { this.tokenBudget = tokenBudget; }
    public int getActualTokens() { return actualTokens; }
    public void setActualTokens(int actualTokens) { this.actualTokens = actualTokens; }
    public boolean isTruncated() { return truncated; }
    public void setTruncated(boolean truncated) { this.truncated = truncated; }
    public ContextTruncationStrategy getTruncationStrategy() { return truncationStrategy; }
    public void setTruncationStrategy(ContextTruncationStrategy truncationStrategy) { this.truncationStrategy = truncationStrategy; }
    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(SecurityLevel securityLevel) { this.securityLevel = securityLevel; }
    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }
}
