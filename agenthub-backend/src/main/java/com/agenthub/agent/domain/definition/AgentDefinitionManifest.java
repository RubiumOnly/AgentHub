package com.agenthub.agent.domain.definition;

import com.agenthub.orchestration.domain.dsl.RetryPolicyDsl;
import com.agenthub.project.domain.context.SecurityLevel;

import java.io.Serializable;
import java.util.*;

public class AgentDefinitionManifest implements Serializable {

    private String role;
    private String objective;
    private String systemPrompt;
    private Map<String, Object> inputSchema = new HashMap<>();
    private Map<String, Object> outputSchema = new HashMap<>();
    private List<String> tools = new ArrayList<>();
    private ProviderPolicy providerPolicy = new ProviderPolicy();
    private List<String> contextSources = new ArrayList<>();
    private AgentBudget budget = new AgentBudget();
    private Long timeoutSeconds = 120L;
    private RetryPolicyDsl retryPolicy = new RetryPolicyDsl(2, 1000L);
    private List<String> stopConditions = new ArrayList<>();
    private SecurityLevel securityLevel = SecurityLevel.INTERNAL;

    public AgentDefinitionManifest() {}

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Map<String, Object> getInputSchema() { return inputSchema; }
    public void setInputSchema(Map<String, Object> inputSchema) { this.inputSchema = inputSchema != null ? inputSchema : new HashMap<>(); }
    public Map<String, Object> getOutputSchema() { return outputSchema; }
    public void setOutputSchema(Map<String, Object> outputSchema) { this.outputSchema = outputSchema != null ? outputSchema : new HashMap<>(); }
    public List<String> getTools() { return tools; }
    public void setTools(List<String> tools) { this.tools = tools != null ? tools : new ArrayList<>(); }
    public ProviderPolicy getProviderPolicy() { return providerPolicy; }
    public void setProviderPolicy(ProviderPolicy providerPolicy) { this.providerPolicy = providerPolicy; }
    public List<String> getContextSources() { return contextSources; }
    public void setContextSources(List<String> contextSources) { this.contextSources = contextSources != null ? contextSources : new ArrayList<>(); }
    public AgentBudget getBudget() { return budget; }
    public void setBudget(AgentBudget budget) { this.budget = budget; }
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public RetryPolicyDsl getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(RetryPolicyDsl retryPolicy) { this.retryPolicy = retryPolicy; }
    public List<String> getStopConditions() { return stopConditions; }
    public void setStopConditions(List<String> stopConditions) { this.stopConditions = stopConditions != null ? stopConditions : new ArrayList<>(); }
    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(SecurityLevel securityLevel) { this.securityLevel = securityLevel; }
}
