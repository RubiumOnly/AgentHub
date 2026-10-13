package com.agenthub.project.dto.context;

import com.agenthub.project.domain.context.ContextSourceType;
import com.agenthub.project.domain.context.SecurityLevel;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * View DTO for Versioned Context Source.
 */
public class ContextSourceView {

    private String id;
    private String projectId;
    private String workspaceId;
    private ContextSourceType sourceType;
    private String name;
    private String filePath;
    private String content;
    private String contentFingerprint;
    private String commitHash;
    private SecurityLevel securityLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Map<String, String> metadata;

    public ContextSourceView() {}

    public ContextSourceView(String id, String projectId, String workspaceId, ContextSourceType sourceType,
                             String name, String filePath, String content, String contentFingerprint,
                             String commitHash, SecurityLevel securityLevel, LocalDateTime createdAt,
                             LocalDateTime updatedAt, Map<String, String> metadata) {
        this.id = id;
        this.projectId = projectId;
        this.workspaceId = workspaceId;
        this.sourceType = sourceType;
        this.name = name;
        this.filePath = filePath;
        this.content = content;
        this.contentFingerprint = contentFingerprint;
        this.commitHash = commitHash;
        this.securityLevel = securityLevel != null ? securityLevel : SecurityLevel.INTERNAL;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public ContextSourceType getSourceType() { return sourceType; }
    public void setSourceType(ContextSourceType sourceType) { this.sourceType = sourceType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getContentFingerprint() { return contentFingerprint; }
    public void setContentFingerprint(String contentFingerprint) { this.contentFingerprint = contentFingerprint; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(SecurityLevel securityLevel) { this.securityLevel = securityLevel; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
