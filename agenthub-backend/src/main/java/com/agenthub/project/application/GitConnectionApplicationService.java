package com.agenthub.project.application;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.identity.infrastructure.security.AuthAuditService;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.project.domain.connection.GitConnectionStatus;
import com.agenthub.project.domain.connection.GitProviderConnection;
import com.agenthub.project.dto.connection.AuthorizeGitConnectionCommand;
import com.agenthub.project.dto.connection.GitConnectionView;
import com.agenthub.project.infrastructure.entity.GitConnectionEntity;
import com.agenthub.project.infrastructure.repository.GitConnectionRepository;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Git provider connection lifecycle (Phase 11 R1).
 *
 * Credentials are persisted as references only and resolved transiently for the
 * ls-remote connectivity probe; verification failures are recorded with masked
 * detail so users see a recoverable reason. Revocation is idempotent and clears
 * the credential reference, blocking any later use of the connection.
 */
@Service
public class GitConnectionApplicationService implements GitConnectionApplication {

    private static final Logger log = LoggerFactory.getLogger(GitConnectionApplicationService.class);

    private final GitConnectionRepository repository;
    private final ResourceOwnershipVerifier ownershipVerifier;

    @Autowired(required = false)
    private AuthAuditService authAuditService;

    @Autowired(required = false)
    private Environment springEnvironment;

    @Value("${agenthub.git.allow-local-test-remote:false}")
    private boolean allowLocalTestRemote = false;

    @Value("${agenthub.git.test-timeout-seconds:15}")
    private int testTimeoutSeconds = 15;

    public GitConnectionApplicationService(GitConnectionRepository repository,
                                           ResourceOwnershipVerifier ownershipVerifier) {
        this.repository = repository;
        this.ownershipVerifier = ownershipVerifier;
    }

    @Override
    @Transactional
    public GitConnectionView authorize(String projectId, AuthorizeGitConnectionCommand cmd) {
        if (cmd == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Authorization command must not be null");
        }
        String actor = currentUserId();
        ownershipVerifier.verifyProjectAccess(projectId, actor, com.agenthub.team.domain.rbac.ProjectPermission.SECRET_MANAGE);

        String branch = cmd.getDefaultBranch() != null && !cmd.getDefaultBranch().isBlank()
                ? cmd.getDefaultBranch() : "main";
        GitProviderConnection connection = new GitProviderConnection(
                null, projectId, cmd.getProvider(), cmd.getRepoUrl(), cmd.getCredentialRef(), branch,
                GitConnectionStatus.ACTIVE, actor);
        connection.validateAuthorization(allowLocalTestRemote);

        String normalizedUrl = GitProviderConnection.normalizeRepoUrl(cmd.getRepoUrl(), allowLocalTestRemote);
        repository.findByProjectIdAndRepoUrlAndStatus(projectId, normalizedUrl, GitConnectionStatus.ACTIVE.name())
                .ifPresent(existing -> {
                    throw new BusinessException(ErrorCode.CONFLICT,
                            "An active connection to this repository already exists: " + existing.getId()
                                    + ". Revoke it before re-authorizing.");
                });

        String credentialRef = cmd.getCredentialRef() != null && !cmd.getCredentialRef().isBlank()
                ? cmd.getCredentialRef().trim() : null;
        GitConnectionEntity entity = new GitConnectionEntity(
                "gitconn-" + UUID.randomUUID().toString().substring(0, 8),
                projectId,
                connection.getProvider().name(),
                normalizedUrl,
                credentialRef,
                branch,
                GitConnectionStatus.ACTIVE.name(),
                actor);
        repository.save(entity);
        audit("GIT_CONNECTION_AUTHORIZED", actor, projectId,
                "Authorized " + connection.getProvider().name() + " connection [" + entity.getId()
                        + "] to [" + SecretMasker.maskUrl(normalizedUrl) + "] credentialRef=["
                        + (credentialRef != null ? credentialRef : "anonymous") + "] branch=[" + branch + "]");
        log.info("Git connection [{}] authorized for project [{}] to [{}]",
                entity.getId(), projectId, SecretMasker.maskUrl(normalizedUrl));
        return toView(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GitConnectionView> list(String projectId) {
        ownershipVerifier.verifyProjectAccess(projectId, currentUserId(), com.agenthub.team.domain.rbac.ProjectPermission.PROJECT_VIEW);
        return repository.findByProjectIdOrderByAuthorizedAtDesc(projectId).stream().map(this::toView).toList();
    }

    @Override
    @Transactional
    public GitConnectionView testConnection(String projectId, String connectionId) {
        String actor = currentUserId();
        ownershipVerifier.verifyProjectAccess(projectId, actor, com.agenthub.team.domain.rbac.ProjectPermission.SECRET_MANAGE);
        GitConnectionEntity entity = loadOwned(projectId, connectionId);
        if (GitConnectionStatus.REVOKED.name().equals(entity.getStatus())) {
            throw new BusinessException(ErrorCode.GIT_CONNECTION_REVOKED,
                    "Connection [" + connectionId + "] was revoked on " + entity.getRevokedAt()
                            + "; re-authorize a new connection to test again.");
        }

        String resolved = entity.getCredentialRef() != null
                ? SecretMasker.resolveSecret(entity.getCredentialRef(), springEnvironment, null) : "";
        try {
            var lsRemote = Git.lsRemoteRepository()
                    .setRemote(entity.getRepoUrl())
                    .setTimeout(testTimeoutSeconds);
            if (resolved != null && !resolved.isBlank()) {
                lsRemote.setCredentialsProvider(new UsernamePasswordCredentialsProvider(resolved, ""));
            }
            Collection<Ref> refs = lsRemote.call();
            boolean branchVisible = refs.stream().anyMatch(r -> r.getName().equals("refs/heads/" + entity.getDefaultBranch()));
            String detail = branchVisible
                    ? "ls-remote ok: " + refs.size() + " refs, default branch [refs/heads/" + entity.getDefaultBranch() + "] visible"
                    : "ls-remote ok: " + refs.size() + " refs, default branch [refs/heads/" + entity.getDefaultBranch() + "] NOT visible";
            entity.recordVerification("SUCCESS", detail);
            audit("GIT_CONNECTION_TESTED", actor, projectId,
                    "Connection [" + connectionId + "] verify SUCCESS: " + detail);
            log.info("Git connection [{}] verified: {}", connectionId, detail);
        } catch (Exception e) {
            String masked = SecretMasker.maskInText(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            entity.recordVerification("FAILED", masked);
            audit("GIT_CONNECTION_TESTED", actor, projectId,
                    "Connection [" + connectionId + "] verify FAILED: " + masked);
            log.warn("Git connection [{}] verification failed: {}", connectionId, masked);
        }
        repository.save(entity);
        return toView(entity);
    }

    @Override
    @Transactional
    public GitConnectionView revoke(String projectId, String connectionId) {
        String actor = currentUserId();
        ownershipVerifier.verifyProjectAccess(projectId, actor, com.agenthub.team.domain.rbac.ProjectPermission.SECRET_MANAGE);
        GitConnectionEntity entity = loadOwned(projectId, connectionId);
        if (GitConnectionStatus.REVOKED.name().equals(entity.getStatus())) {
            // Idempotent: an already-revoked connection returns its current state without error or re-audit.
            return toView(entity);
        }
        entity.markRevoked(actor);
        repository.save(entity);
        audit("GIT_CONNECTION_REVOKED", actor, projectId,
                "Revoked " + entity.getProvider() + " connection [" + connectionId
                        + "] to [" + SecretMasker.maskUrl(entity.getRepoUrl()) + "]; credential reference cleared");
        log.info("Git connection [{}] revoked by [{}]; credential reference cleared", connectionId, actor);
        return toView(entity);
    }

    private GitConnectionEntity loadOwned(String projectId, String connectionId) {
        return repository.findByIdAndProjectId(connectionId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GIT_CONNECTION_NOT_FOUND,
                        "Git connection not found in project [" + projectId + "]: " + connectionId));
    }

    private String currentUserId() {
        String userId = RequestContext.get().getUserId();
        if (userId == null || userId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED,
                    "Git connection operations require an authenticated user");
        }
        return userId;
    }

    private void audit(String eventType, String actor, String projectId, String details) {
        if (authAuditService != null) {
            authAuditService.record(eventType, actor, null, null, "project=" + projectId + " " + details);
        }
    }

    private GitConnectionView toView(GitConnectionEntity entity) {
        GitConnectionView view = new GitConnectionView();
        view.setId(entity.getId());
        view.setProjectId(entity.getProjectId());
        view.setProvider(entity.getProvider());
        view.setRepoUrl(entity.getRepoUrl());
        view.setCredentialRef(entity.getCredentialRef());
        view.setDefaultBranch(entity.getDefaultBranch());
        view.setStatus(entity.getStatus());
        view.setAuthorizedBy(entity.getAuthorizedBy());
        view.setAuthorizedAt(entity.getAuthorizedAt());
        view.setRevokedBy(entity.getRevokedBy());
        view.setRevokedAt(entity.getRevokedAt());
        view.setLastVerifiedAt(entity.getLastVerifiedAt());
        view.setLastVerifyResult(entity.getLastVerifyResult());
        view.setLastVerifyDetail(entity.getLastVerifyDetail());
        view.setCreatedAt(entity.getCreatedAt());
        view.setUpdatedAt(entity.getUpdatedAt());
        return view;
    }
}
