package com.agenthub.agent.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_definitions")
public class AgentDefinitionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "def_key", nullable = false, unique = true, length = 64)
    private String defKey;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String role;

    @Column(columnDefinition = "TEXT")
    private String manifest;

    @Column(nullable = false, length = 32)
    private String version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AgentDefinitionEntity() {}

    public AgentDefinitionEntity(String id, String defKey, String name, String role, String manifest, String version) {
        this.id = id;
        this.defKey = defKey;
        this.name = name;
        this.role = role;
        this.manifest = manifest;
        this.version = version != null ? version : "1.0.0";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getDefKey() { return defKey; }
    public void setDefKey(String defKey) { this.defKey = defKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
