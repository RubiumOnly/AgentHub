package com.agenthub.orchestration.dto;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

public class CreateWorkflowDefinitionCommand {
    private String name;
    private String description;
    private String version = "1.0.0";
    private WorkflowDsl dsl;
    private String dslJson;

    public CreateWorkflowDefinitionCommand() {}

    public CreateWorkflowDefinitionCommand(String name, String description, WorkflowDsl dsl) {
        this.name = name;
        this.description = description;
        this.dsl = dsl;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public WorkflowDsl getDsl() { return dsl; }
    public void setDsl(WorkflowDsl dsl) { this.dsl = dsl; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
}
