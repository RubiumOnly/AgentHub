package com.agenthub.orchestration.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "workflow_definitions")
public class WorkflowDefinitionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "schema_version", nullable = false, length = 16)
    private String schemaVersion;

    @Column(name = "dsl_json", nullable = false, columnDefinition = "TEXT")
    private String dslJson;

    @Column(length = 64)
    private String checksum;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public WorkflowDefinitionEntity() {}

    public WorkflowDefinitionEntity(String id, String version, String schemaVersion, String dslJson, String checksum) {
        this.id = id;
        this.version = version != null ? version : "1.0.0";
        this.schemaVersion = schemaVersion != null ? schemaVersion : "v1";
        this.dslJson = dslJson;
        this.checksum = checksum;
        this.createdAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getDslJson() { return dslJson; }
    public void setDslJson(String dslJson) { this.dslJson = dslJson; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
