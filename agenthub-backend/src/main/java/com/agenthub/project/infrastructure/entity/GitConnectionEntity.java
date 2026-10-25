package com.agenthub.project.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Persisted Git provider connection (Phase 11). credential_ref stores a secret
 * REFERENCE only (env:/prop:/cfg:/secret:); resolved secrets never reach this row.
 * Revocation clears credential_ref and is terminal for the row.
 */
@Entity
@Table(name = "git_connections")
public class GitConnectionEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "project_id", nullable = false, length = 64)
    private String projectId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "repo_url", nullable = false, length = 512)
    private String repoUrl;

    @Column(name = "credential_ref", length = 255)
    private String credentialRef;

    @Column(name = "default_branch", nullable = false, length = 128)
    private String defaultBranch;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "authorized_by", nullable = false, length = 64)
    private String authorizedBy;

    @Column(name = "authorized_at", nullable = false)
    private LocalDateTime authorizedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_verify_result", length = 32)
    private String lastVerifyResult;

    @Column(name = "last_verify_detail", length = 512)
    private String lastVerifyDetail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public GitConnectionEntity() {}

    public GitConnectionEntity(String id, String projectId, String provider, String repoUrl, String credentialRef,
                               String defaultBranch, String status, String authorizedBy) {
        this.id = id;
        this.projectId = projectId;
        this.provider = provider;
        this.repoUrl = repoUrl;
        this.credentialRef = credentialRef;
        this.defaultBranch = defaultBranch;
        this.status = status;
        this.authorizedBy = authorizedBy;
        this.authorizedAt = LocalDateTime.now();
        this.createdAt = this.authorizedAt;
        this.updatedAt = this.authorizedAt;
    }

    public void markRevoked(String revokedBy) {
        this.status = "REVOKED";
        this.revokedBy = revokedBy;
        this.revokedAt = LocalDateTime.now();
        this.credentialRef = null;
        this.updatedAt = this.revokedAt;
    }

    public void recordVerification(String result, String detail) {
        this.lastVerifiedAt = LocalDateTime.now();
        this.lastVerifyResult = result;
        this.lastVerifyDetail = detail != null && detail.length() > 512 ? detail.substring(0, 512) : detail;
        this.updatedAt = this.lastVerifiedAt;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public String getProvider() { return provider; }
    public String getRepoUrl() { return repoUrl; }
    public String getCredentialRef() { return credentialRef; }
    public String getDefaultBranch() { return defaultBranch; }
    public String getStatus() { return status; }
    public String getAuthorizedBy() { return authorizedBy; }
    public LocalDateTime getAuthorizedAt() { return authorizedAt; }
    public String getRevokedBy() { return revokedBy; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getLastVerifiedAt() { return lastVerifiedAt; }
    public String getLastVerifyResult() { return lastVerifyResult; }
    public String getLastVerifyDetail() { return lastVerifyDetail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
