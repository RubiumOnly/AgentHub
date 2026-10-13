package com.agenthub.project.domain.source;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

/**
 * Git Source Configuration with credential masking and path sanitization.
 */
public class GitSourceConfig {

    private GitSourceType sourceType = GitSourceType.LOCAL_DIR;
    private String location;
    private String repoUrl;
    private String secretRef;
    private String defaultBranch = "main";

    public GitSourceConfig() {}

    public GitSourceConfig(GitSourceType sourceType, String location, String repoUrl, String secretRef, String defaultBranch) {
        this.sourceType = sourceType != null ? sourceType : GitSourceType.LOCAL_DIR;
        this.location = location;
        this.repoUrl = repoUrl;
        this.secretRef = secretRef;
        if (defaultBranch != null && !defaultBranch.isBlank()) {
            this.defaultBranch = defaultBranch.trim();
        }
    }

    public void validate() {
        if (sourceType == GitSourceType.LOCAL_DIR) {
            if (location == null || location.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Local controlled source location must not be blank");
            }
            String norm = location.replace('\\', '/').trim();
            if (norm.startsWith("/") || norm.matches("^[a-zA-Z]:.*") || norm.contains("..")) {
                throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                        "Arbitrary host absolute paths or directory traversals are strictly prohibited for local source: " + location);
            }
        } else if (sourceType == GitSourceType.REMOTE_GIT) {
            if (repoUrl == null || repoUrl.isBlank()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Remote repository URL must not be blank");
            }
            String normUrl = repoUrl.trim().toLowerCase();
            if (!normUrl.startsWith("https://") && !normUrl.startsWith("http://") && !normUrl.startsWith("git://")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "Remote repository URL must use HTTPS/HTTP/GIT protocol");
            }
            if (secretRef != null && (secretRef.contains("ghp_") || secretRef.contains("github_pat_") || secretRef.startsWith("sk-"))) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Raw credential detected in secretRef. Only secret references (e.g., env:GITHUB_TOKEN or secret:key) are permitted");
            }
        }
    }

    public String getMaskedIdentifier() {
        if (sourceType == GitSourceType.LOCAL_DIR) {
            return location != null ? location : "local";
        }
        return SecretMasker.maskUrl(repoUrl);
    }

    public GitSourceType getSourceType() { return sourceType; }
    public void setSourceType(GitSourceType sourceType) { this.sourceType = sourceType; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }

    public String getSecretRef() { return secretRef; }
    public void setSecretRef(String secretRef) { this.secretRef = secretRef; }

    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
}
