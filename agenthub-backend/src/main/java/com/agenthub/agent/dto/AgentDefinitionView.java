package com.agenthub.agent.dto;

import java.time.LocalDateTime;

public class AgentDefinitionView {
    private String id;
    private String defKey;
    private String name;
    private String role;
    private String manifest;
    private String version;
    private LocalDateTime createdAt;

    public AgentDefinitionView() {}

    public AgentDefinitionView(String id, String defKey, String name, String role, String manifest, String version, LocalDateTime createdAt) {
        this.id = id;
        this.defKey = defKey;
        this.name = name;
        this.role = role;
        this.manifest = manifest;
        this.version = version;
        this.createdAt = createdAt;
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
}
