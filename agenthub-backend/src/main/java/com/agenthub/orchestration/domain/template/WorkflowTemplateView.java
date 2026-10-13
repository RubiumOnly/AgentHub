package com.agenthub.orchestration.domain.template;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

import java.util.List;

public class WorkflowTemplateView {

    private String templateId;
    private WorkflowTemplateType templateType;
    private String name;
    private String description;
    private List<String> stages;
    private List<String> requiredAgentRoles;
    private WorkflowDsl dsl;

    public WorkflowTemplateView() {}

    public WorkflowTemplateView(String templateId,
                                WorkflowTemplateType templateType,
                                String name,
                                String description,
                                List<String> stages,
                                List<String> requiredAgentRoles,
                                WorkflowDsl dsl) {
        this.templateId = templateId;
        this.templateType = templateType;
        this.name = name;
        this.description = description;
        this.stages = stages;
        this.requiredAgentRoles = requiredAgentRoles;
        this.dsl = dsl;
    }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public WorkflowTemplateType getTemplateType() { return templateType; }
    public void setTemplateType(WorkflowTemplateType templateType) { this.templateType = templateType; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<String> getStages() { return stages; }
    public void setStages(List<String> stages) { this.stages = stages; }
    public List<String> getRequiredAgentRoles() { return requiredAgentRoles; }
    public void setRequiredAgentRoles(List<String> requiredAgentRoles) { this.requiredAgentRoles = requiredAgentRoles; }
    public WorkflowDsl getDsl() { return dsl; }
    public void setDsl(WorkflowDsl dsl) { this.dsl = dsl; }
}
