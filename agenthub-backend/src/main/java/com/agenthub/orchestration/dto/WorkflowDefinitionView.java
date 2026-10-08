package com.agenthub.orchestration.dto;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

import java.time.LocalDateTime;

public class WorkflowDefinitionView {
    private String id;
    private String name;
    private String description;
    private String version;
    private String schemaVersion;
    private String dslJson;
    private WorkflowDsl dsl;
    private String checksum;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public WorkflowDefinitionView() {}

    public WorkflowDefinitionView(String id, String name, String description, String version,
                                  String schemaVersion, String dslJson, WorkflowDsl dsl,
                                  String checksum, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.version = version;
        this.schemaVersion = schemaVersion;
        this.dslJson = dslJson;
        this.dsl = dsl;
        this.checksum = checksum;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
    public WorkflowDsl getDsl() { return dsl; }
    public void setDsl(WorkflowDsl dsl) { this.dsl = dsl; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
