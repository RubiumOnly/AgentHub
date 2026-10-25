package com.agenthub.project.dto.connection;

import com.agenthub.project.domain.connection.GitProviderType;

/**
 * Command to authorize a Git provider connection for a project.
 * credentialRef must be a secret reference (env:NAME / prop:key / cfg:key / secret:name);
 * raw token values are rejected before persistence.
 */
public class AuthorizeGitConnectionCommand {

    private GitProviderType provider = GitProviderType.GITHUB;
    private String repoUrl;
    private String credentialRef;
    private String defaultBranch = "main";

    public GitProviderType getProvider() { return provider; }
    public void setProvider(GitProviderType provider) {
        if (provider != null) {
            this.provider = provider;
        }
    }
    public String getRepoUrl() { return repoUrl; }
    public void setRepoUrl(String repoUrl) { this.repoUrl = repoUrl; }
    public String getCredentialRef() { return credentialRef; }
    public void setCredentialRef(String credentialRef) { this.credentialRef = credentialRef; }
    public String getDefaultBranch() { return defaultBranch; }
    public void setDefaultBranch(String defaultBranch) { this.defaultBranch = defaultBranch; }
}
