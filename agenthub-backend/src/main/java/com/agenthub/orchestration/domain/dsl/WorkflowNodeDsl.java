package com.agenthub.orchestration.domain.dsl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WorkflowNodeDsl implements Serializable {
    private String id;
    private String name;
    private String type = "AGENT"; // START, AGENT, CONDITION, APPROVAL, JOIN, END
    private String agentPlatform;  // CLAUDE_CODE, CODEX, DEEPSEEK, OPENCLAW, SPRING_AI_API, MOCK
    private String promptTemplate;
    private Map<String, Object> inputs = new HashMap<>();
    private String condition;      // condition expression
    private String joinPolicy = "all_succeeded"; // all_succeeded, any_succeeded, custom_condition
    private boolean requiresApproval = false;
    private Long timeoutSeconds;
    private RetryPolicyDsl retryPolicy = new RetryPolicyDsl(0, 500L);
    private List<String> nextNodeIds = new ArrayList<>();

    public WorkflowNodeDsl() {}

    public WorkflowNodeDsl(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
    }

    public WorkflowNodeDsl(String id, String name, String type, String agentPlatform, String promptTemplate) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.agentPlatform = agentPlatform;
        this.promptTemplate = promptTemplate;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getAgentPlatform() { return agentPlatform; }
    public void setAgentPlatform(String agentPlatform) { this.agentPlatform = agentPlatform; }
    public String getPromptTemplate() { return promptTemplate; }
    public void setPromptTemplate(String promptTemplate) { this.promptTemplate = promptTemplate; }
    public Map<String, Object> getInputs() { return inputs; }
    public void setInputs(Map<String, Object> inputs) { this.inputs = inputs != null ? inputs : new HashMap<>(); }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
    public String getJoinPolicy() { return joinPolicy; }
    public void setJoinPolicy(String joinPolicy) { this.joinPolicy = joinPolicy; }
    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }
    public Long getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public RetryPolicyDsl getRetryPolicy() { return retryPolicy; }
    public void setRetryPolicy(RetryPolicyDsl retryPolicy) { this.retryPolicy = retryPolicy; }
    public List<String> getNextNodeIds() { return nextNodeIds; }
    public void setNextNodeIds(List<String> nextNodeIds) { this.nextNodeIds = nextNodeIds != null ? nextNodeIds : new ArrayList<>(); }
}
