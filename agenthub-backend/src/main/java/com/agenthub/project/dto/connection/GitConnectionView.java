package com.agenthub.project.dto.connection;

import java.time.LocalDateTime;

/**
 * Projection of a Git provider connection. Exposes the credential REFERENCE
 * (never a resolved secret) so auditors can see which secret slot is bound.
 */
public class GitConnectionView {

    private String id;
    private String projectId;
    private String provider;
    private String repoUrl;
    private String credentialRef;
    private String defaultBranch;
    private String status;
    private String authorizedBy;
    private LocalDateTime authorizedAt;
    private String revokedBy;
    private LocalDateTime revokedAt;
    private LocalDateTime lastVerifiedAt;
    private String lastVerifyResult;
    private String lastVerifyDetail;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAuthorizedBy() { return authorizedBy; }
    public void setAuthorizedBy(String authorizedBy) { this.authorizedBy = authorizedBy; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public void setAuthorizedAt(LocalDateTime authorizedAt) { this.authorizedAt = authorizedAt; }
    public String getRevokedBy() { return revokedBy; }
    public void setRevokedBy(String revokedBy) { this.revokedBy = revokedBy; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public void setRevokedAt(LocalDateTime revokedAt) { this.revokedAt = revokedAt; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public void setLastVerifiedAt(LocalDateTime lastVerifiedAt) { this.lastVerifiedAt = lastVerifiedAt; }
    public String getLastVerifyResult() { return lastVerifyResult; }
    public void setLastVerifyResult(String lastVerifyResult) { this.lastVerifyResult = lastVerifyResult; }
    public String getLastVerifyDetail() { return lastVerifyDetail; }
    public void setLastVerifyDetail(String lastVerifyDetail) { this.lastVerifyDetail = lastVerifyDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
