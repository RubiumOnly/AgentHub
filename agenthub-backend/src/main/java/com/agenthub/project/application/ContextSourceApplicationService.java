package com.agenthub.project.application;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.identity.infrastructure.security.ResourceOwnershipVerifier;
import com.agenthub.project.domain.context.ContextSourceType;
import com.agenthub.project.domain.context.SecurityLevel;
import com.agenthub.project.dto.context.ContextSourceView;
import com.agenthub.project.dto.context.RegisterContextSourceCommand;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing versioned Context Sources (AOCI Cognition, Project Rules, Build Config, Test Conventions).
 */
@Service
public class ContextSourceApplicationService implements ContextSourceApplication {

    private static final Logger log = LoggerFactory.getLogger(ContextSourceApplicationService.class);

    private final ArtifactRepository artifactRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceResolver workspaceResolver;
    private final ResourceOwnershipVerifier ownershipVerifier;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // In-memory cache for fast retrieval and dynamic context sources
    private final Map<String, List<ContextSourceView>> memoryCache = new ConcurrentHashMap<>();

    @Autowired
    public ContextSourceApplicationService(ArtifactRepository artifactRepository,
                                          WorkspaceRepository workspaceRepository,
                                          WorkspaceResolver workspaceResolver,
                                          ResourceOwnershipVerifier ownershipVerifier) {
        this.artifactRepository = artifactRepository;
        this.workspaceRepository = workspaceRepository;
        this.workspaceResolver = workspaceResolver;
        this.ownershipVerifier = ownershipVerifier;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContextSourceView> listContextSources(String projectId) {
        ownershipVerifier.verifyProjectAccess(projectId);
        List<ContextSourceView> results = new ArrayList<>();

        // 1. From database artifacts
        if (artifactRepository != null) {
            List<ArtifactEntity> entities = artifactRepository.findByRunIdAndArtifactType(projectId, "CONTEXT_SOURCE");
            for (ArtifactEntity entity : entities) {
                results.add(mapEntityToView(entity, projectId));
            }
        }

        // 2. From memory cache
        List<ContextSourceView> cached = memoryCache.get(projectId);
        if (cached != null) {
            for (ContextSourceView c : cached) {
                if (results.stream().noneMatch(r -> r.getId().equals(c.getId()))) {
                    results.add(c);
                }
            }
        }

        // 3. If empty, auto discover from workspace
        if (results.isEmpty() && workspaceRepository != null) {
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(projectId);
            if (wsOpt.isPresent()) {
                WorkspaceEntity ws = wsOpt.get();
                List<ContextSourceView> discovered = autoDiscoverContextSources(projectId, ws.getId(), ws.getGitBaselineCommit());
                results.addAll(discovered);
            }
        }

        return results;
    }

    @Override
    @Transactional(readOnly = true)
    public ContextSourceView getContextSource(String projectId, String sourceId) {
        ownershipVerifier.verifyProjectAccess(projectId);
        List<ContextSourceView> all = listContextSources(projectId);
        return all.stream()
                .filter(s -> s.getId().equals(sourceId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.CONTEXT_SOURCE_NOT_FOUND, "Context source not found: " + sourceId));
    }

    @Override
    @Transactional
    public ContextSourceView registerContextSource(String projectId, RegisterContextSourceCommand cmd) {
        ownershipVerifier.verifyProjectAccess(projectId);
        if (cmd == null || cmd.getName() == null || cmd.getName().isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Context source name must not be blank");
        }

        String workspaceId = "";
        String commitHash = cmd.getCommitHash() != null ? cmd.getCommitHash() : "";
        if (workspaceRepository != null) {
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findByProjectId(projectId);
            if (wsOpt.isPresent()) {
                workspaceId = wsOpt.get().getId();
                if (commitHash.isBlank() && wsOpt.get().getGitBaselineCommit() != null) {
                    commitHash = wsOpt.get().getGitBaselineCommit();
                }
            }
        }

        String content = cmd.getContent() != null ? cmd.getContent() : "";
        String fingerprint = JGitWorkspaceManager.calculateBytesChecksum(content.getBytes(StandardCharsets.UTF_8));
        String sourceId = "ctx-" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("name", cmd.getName());
        meta.put("filePath", cmd.getFilePath() != null ? cmd.getFilePath() : "");
        meta.put("sourceType", cmd.getSourceType().name());
        meta.put("securityLevel", cmd.getSecurityLevel().name());
        meta.put("content", content);
        if (cmd.getMetadata() != null) {
            meta.putAll(cmd.getMetadata());
        }

        String metaJson;
        try {
            metaJson = objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            metaJson = "{}";
        }

        if (artifactRepository != null) {
            ArtifactEntity entity = new ArtifactEntity(
                    sourceId,
                    projectId,
                    commitHash,
                    "CONTEXT_SOURCE",
                    cmd.getFilePath() != null ? cmd.getFilePath() : cmd.getSourceType().name(),
                    fingerprint,
                    metaJson
            );
            entity.setReviewStatus("ACCEPTED");
            artifactRepository.save(entity);
        }

        ContextSourceView view = new ContextSourceView(
                sourceId,
                projectId,
                workspaceId,
                cmd.getSourceType(),
                cmd.getName(),
                cmd.getFilePath(),
                content,
                fingerprint,
                commitHash,
                cmd.getSecurityLevel(),
                LocalDateTime.now(),
                LocalDateTime.now(),
                meta
        );

        memoryCache.computeIfAbsent(projectId, k -> new ArrayList<>()).add(view);
        log.info("Registered versioned ContextSource [{}] type=[{}] for project [{}] commit=[{}] fingerprint=[{}]",
                sourceId, cmd.getSourceType(), projectId, commitHash, fingerprint);
        return view;
    }

    @Override
    public List<ContextSourceView> autoDiscoverContextSources(String projectId, String workspaceId, String commitHash) {
        List<ContextSourceView> list = new ArrayList<>();
        if (workspaceResolver == null || workspaceId == null || workspaceId.isBlank()) {
            return list;
        }

        try {
            Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceId);
            if (!Files.exists(wsRoot)) {
                return list;
            }

            // 1. Discover Project Rules (PROJECT_RULES.md or AGENTS.md)
            Path rulesFile = wsRoot.resolve("PROJECT_RULES.md");
            if (Files.exists(rulesFile)) {
                String content = Files.readString(rulesFile, StandardCharsets.UTF_8);
                RegisterContextSourceCommand cmd = new RegisterContextSourceCommand(
                        ContextSourceType.PROJECT_RULES,
                        "Project Engineering Rules",
                        "PROJECT_RULES.md",
                        content,
                        commitHash,
                        SecurityLevel.INTERNAL,
                        Map.of("category", "engineering_rules")
                );
                list.add(registerContextSource(projectId, cmd));
            }

            // 2. Discover Build Config (pom.xml or package.json)
            Path pomFile = wsRoot.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                String content = Files.readString(pomFile, StandardCharsets.UTF_8);
                RegisterContextSourceCommand cmd = new RegisterContextSourceCommand(
                        ContextSourceType.BUILD_CONFIG,
                        "Maven Build Configuration",
                        "pom.xml",
                        content,
                        commitHash,
                        SecurityLevel.INTERNAL,
                        Map.of("buildTool", "maven", "language", "java")
                );
                list.add(registerContextSource(projectId, cmd));
            }

            Path pkgFile = wsRoot.resolve("package.json");
            if (Files.exists(pkgFile)) {
                String content = Files.readString(pkgFile, StandardCharsets.UTF_8);
                RegisterContextSourceCommand cmd = new RegisterContextSourceCommand(
                        ContextSourceType.BUILD_CONFIG,
                        "NPM Package Configuration",
                        "package.json",
                        content,
                        commitHash,
                        SecurityLevel.INTERNAL,
                        Map.of("buildTool", "npm", "runtime", "node")
                );
                list.add(registerContextSource(projectId, cmd));
            }

            // 3. AOCI Cognition Reference
            String aociSnippet = String.format("AOCI Code Cognition Managed Scope aligned with commit [%s]",
                    commitHash != null && !commitHash.isBlank() ? commitHash : "HEAD");
            RegisterContextSourceCommand aociCmd = new RegisterContextSourceCommand(
                    ContextSourceType.AOCI_COGNITION,
                    "AOCI Code & Database Cognition Baseline",
                    "docs/AOCI_COGNITION.md",
                    aociSnippet,
                    commitHash,
                    SecurityLevel.INTERNAL,
                    Map.of("status", "aligned", "scope", "backend,frontend,schema")
            );
            list.add(registerContextSource(projectId, aociCmd));

        } catch (Exception e) {
            log.warn("Auto discovery of context sources for project [{}] workspace [{}] had warning: {}",
                    projectId, workspaceId, e.getMessage());
        }

        return list;
    }

    private ContextSourceView mapEntityToView(ArtifactEntity entity, String projectId) {
        String name = entity.getPathOrRef();
        String filePath = entity.getPathOrRef();
        ContextSourceType type = ContextSourceType.PROJECT_RULES;
        SecurityLevel level = SecurityLevel.INTERNAL;
        String content = "";
        Map<String, String> meta = new LinkedHashMap<>();

        if (entity.getMetadataJson() != null && !entity.getMetadataJson().isBlank()) {
            try {
                meta = objectMapper.readValue(entity.getMetadataJson(), new TypeReference<Map<String, String>>() {});
                if (meta.containsKey("name")) name = meta.get("name");
                if (meta.containsKey("filePath")) filePath = meta.get("filePath");
                if (meta.containsKey("sourceType")) type = ContextSourceType.valueOf(meta.get("sourceType"));
                if (meta.containsKey("securityLevel")) level = SecurityLevel.valueOf(meta.get("securityLevel"));
                if (meta.containsKey("content")) content = meta.get("content");
            } catch (Exception ignored) {}
        }

        return new ContextSourceView(
                entity.getId(),
                projectId,
                "",
                type,
                name,
                filePath,
                content,
                entity.getChecksum(),
                entity.getStepRunId(),
                level,
                entity.getCreatedAt(),
                entity.getReviewedAt() != null ? entity.getReviewedAt() : entity.getCreatedAt(),
                meta
        );
    }
}
