package com.agenthub.agent.domain.definition;

public class AgentToolDefinition {

    private String name;
    private AgentToolCategory category;
    private String description;
    private boolean highImpact;
    private String parameterSchemaJson;
    private long maxOutputSizeBytes;

    public AgentToolDefinition() {}

    public AgentToolDefinition(String name,
                               AgentToolCategory category,
                               String description,
                               boolean highImpact,
                               String parameterSchemaJson,
                               long maxOutputSizeBytes) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.highImpact = highImpact;
        this.parameterSchemaJson = parameterSchemaJson;
        this.maxOutputSizeBytes = maxOutputSizeBytes;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AgentToolCategory getCategory() { return category; }
    public void setCategory(AgentToolCategory category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isHighImpact() { return highImpact; }
    public void setHighImpact(boolean highImpact) { this.highImpact = highImpact; }
    public String getParameterSchemaJson() { return parameterSchemaJson; }
    public void setParameterSchemaJson(String parameterSchemaJson) { this.parameterSchemaJson = parameterSchemaJson; }
    public long getMaxOutputSizeBytes() { return maxOutputSizeBytes; }
    public void setMaxOutputSizeBytes(long maxOutputSizeBytes) { this.maxOutputSizeBytes = maxOutputSizeBytes; }
}
