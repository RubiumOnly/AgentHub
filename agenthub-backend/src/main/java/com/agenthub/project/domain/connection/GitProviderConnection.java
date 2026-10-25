package com.agenthub.project.domain.connection;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;

import java.util.regex.Pattern;

/**
 * Git provider connection aggregate (Phase 11 R1).
 *
 * Invariants enforced here:
 * - The repository URL must be HTTPS in production; http/file schemes are only
 *   accepted when the host explicitly enables local test remotes.
 * - Credentials are REFERENCES ONLY (env:/prop:/cfg:/secret: scheme). Raw token
 *   shapes (ghp_*, github_pat_*, sk-*) are rejected outright.
 * - The default branch must be a sanitized ref name.
 */
public class GitProviderConnection {

    private static final Pattern CREDENTIAL_REF_PATTERN = Pattern.compile("^(env|prop|cfg|secret):[A-Za-z0-9_.\\-/]{1,200}$");
    private static final Pattern BRANCH_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._\\-/]{0,127}$");

    private final String id;
    private final String projectId;
    private final GitProviderType provider;
    private final String repoUrl;
    private final String credentialRef;
    private final String defaultBranch;
    private final GitConnectionStatus status;
    private final String authorizedBy;

    public GitProviderConnection(String id, String projectId, GitProviderType provider, String repoUrl,
                                 String credentialRef, String defaultBranch, GitConnectionStatus status,
                                 String authorizedBy) {
        this.id = id;
        this.projectId = projectId;
        this.provider = provider != null ? provider : GitProviderType.GITHUB;
        this.repoUrl = repoUrl;
        this.credentialRef = credentialRef;
        this.defaultBranch = defaultBranch;
        this.status = status;
        this.authorizedBy = authorizedBy;
    }

    public void validateAuthorization(boolean localTestRemotesAllowed) {
        if (projectId == null || projectId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Project ID must not be blank");
        }
        if (authorizedBy == null || authorizedBy.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Git connection authorization requires an authenticated user");
        }
        String url = normalizeRepoUrl(repoUrl, localTestRemotesAllowed);
        if (url == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Repository URL must use the https:// scheme (http:// and file:// are only permitted for local test remotes)");
        }
        if (credentialRef != null && !credentialRef.isBlank()) {
            String ref = credentialRef.trim();
            if (!CREDENTIAL_REF_PATTERN.matcher(ref).matches()) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "credentialRef must be a secret reference (env:NAME, prop:key, cfg:key or secret:name)");
            }
            if (ref.contains("ghp_") || ref.contains("github_pat_") || ref.contains("sk-")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR,
                        "Raw credential detected in credentialRef. Only secret references are persisted, never token values");
            }
        }
        String branch = defaultBranch == null ? "" : defaultBranch.trim();
        if (!BRANCH_PATTERN.matcher(branch).matches()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Default branch must be a sanitized git ref name (letters, digits, '.', '_', '-', '/')");
        }
    }

    /**
     * Returns the normalized lowercase URL, or null when the scheme is not acceptable
     * for the current trust level.
     */
    public static String normalizeRepoUrl(String repoUrl) {
        return normalizeRepoUrl(repoUrl, false);
    }

    public static String normalizeRepoUrl(String repoUrl, boolean localTestRemotesAllowed) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        String url = repoUrl.trim();
        boolean https = url.toLowerCase().startsWith("https://");
        boolean localOnly = url.toLowerCase().startsWith("http://") || url.toLowerCase().startsWith("file:");
        if (!https && !(localOnly && localTestRemotesAllowed)) {
            return null;
        }
        if (url.toLowerCase().startsWith("file:")) {
            return url;
        }
        return url.toLowerCase();
    }

    public boolean isRevoked() {
        return status == GitConnectionStatus.REVOKED;
    }

    public String getId() { return id; }
    public String getProjectId() { return projectId; }
    public GitProviderType getProvider() { return provider; }
    public String getRepoUrl() { return repoUrl; }
    public String getCredentialRef() { return credentialRef; }
    public String getDefaultBranch() { return defaultBranch; }
    public GitConnectionStatus getStatus() { return status; }
    public String getAuthorizedBy() { return authorizedBy; }
}
