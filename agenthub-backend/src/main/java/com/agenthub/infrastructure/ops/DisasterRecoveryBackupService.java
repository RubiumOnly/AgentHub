package com.agenthub.infrastructure.ops;

import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.repository.WorkflowRunRepository;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.project.infrastructure.entity.ProjectEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.ProjectRepository;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.FileSystemUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Phase E Production Disaster Recovery Backup, Checksum, Encryption, Restore & Safe Cleanup Engine.
 * Supports:
 * 1. Consistent point-in-time DB + Workspace snapshots with SHA-256 checksums (backup-manifest.json & SHA256SUMS);
 * 2. Optional AES-256-GCM authenticated encryption (PBKDF2-HMAC-SHA256);
 * 3. Pre-restore checksum verification and tamper rejection;
 * 4. Clean-environment restoration with DB row count, Workspace file checksum, and Run sample verification;
 * 5. Retention policy rotation on backup archives;
 * 6. Database-reconciled Workspace archival cleanup (forbidding blind mtime-based deletion of active workspaces).
 */
@Service
public class DisasterRecoveryBackupService {

    private static final Logger log = LoggerFactory.getLogger(DisasterRecoveryBackupService.class);
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int SALT_LENGTH = 16;
    private static final int PBKDF2_ITERATIONS = 65_536;

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkflowRunRepository workflowRunRepository;
    private final StepRunRepository stepRunRepository;
    private final RunEventRepository runEventRepository;
    private final ArtifactRepository artifactRepository;
    private final ApprovalRepository approvalRepository;
    private final DeploymentRepository deploymentRepository;
    private final WorkspaceLockManager workspaceLockManager;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Flyway flyway;

    public DisasterRecoveryBackupService(
            ProjectRepository projectRepository,
            WorkspaceRepository workspaceRepository,
            WorkflowRunRepository workflowRunRepository,
            StepRunRepository stepRunRepository,
            RunEventRepository runEventRepository,
            ArtifactRepository artifactRepository,
            ApprovalRepository approvalRepository,
            DeploymentRepository deploymentRepository,
            WorkspaceLockManager workspaceLockManager,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            @Autowired(required = false) Flyway flyway) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
        this.workflowRunRepository = workflowRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.runEventRepository = runEventRepository;
        this.artifactRepository = artifactRepository;
        this.approvalRepository = approvalRepository;
        this.deploymentRepository = deploymentRepository;
        this.workspaceLockManager = workspaceLockManager;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.flyway = flyway;
    }

    public static class DatabaseSnapshotPayload {
        public List<ProjectEntity> projects = new ArrayList<>();
        public List<WorkspaceEntity> workspaces = new ArrayList<>();
        public List<WorkflowRunEntity> workflowRuns = new ArrayList<>();
        public List<StepRunEntity> stepRuns = new ArrayList<>();
        public List<RunEventEntity> runEvents = new ArrayList<>();
        public List<ArtifactEntity> artifacts = new ArrayList<>();
        public List<ApprovalEntity> approvals = new ArrayList<>();
        public List<DeploymentEntity> deployments = new ArrayList<>();
    }

    public static class BackupManifest {
        public String backupId;
        public String consistentPointUtc;
        public String flywaySchemaVersion;
        public boolean encrypted;
        public String encryptionAlgorithm;
        public String dbSnapshotFileName;
        public String dbSnapshotSha256;
        public Map<String, Integer> tableCounts = new LinkedHashMap<>();
        public Map<String, String> workspaceFileChecksums = new LinkedHashMap<>();
        public Map<String, String> sampledRunFingerprints = new LinkedHashMap<>();
        public long backupDurationMs;
    }

    public record RestoreVerificationReport(
            String backupId,
            boolean checksumVerified,
            boolean schemaVersionVerified,
            boolean sampledRunsVerified,
            int restoredProjectsCount,
            int restoredRunsCount,
            int restoredStepsCount,
            int restoredEventsCount,
            int restoredArtifactsCount,
            int restoredDeploymentsCount,
            int restoredWorkspaceFilesCount,
            long restoreDurationMs
    ) {}

    public BackupManifest createBackup(Path workspaceRoot, Path backupOutputDir, String encryptionPassphrase) throws Exception {
        long startNs = System.nanoTime();
        Files.createDirectories(backupOutputDir);

        String backupId = "bkp-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 6);
        String consistentPointUtc = Instant.now().toString();

        // 1. Capture consistent DB snapshot inside read transaction
        DatabaseSnapshotPayload snapshot = transactionTemplate.execute(status -> {
            DatabaseSnapshotPayload payload = new DatabaseSnapshotPayload();
            payload.projects = projectRepository.findAll();
            payload.workspaces = workspaceRepository.findAll();
            payload.workflowRuns = workflowRunRepository.findAll();
            payload.stepRuns = stepRunRepository.findAll();
            payload.runEvents = runEventRepository.findAll();
            payload.artifacts = artifactRepository.findAll();
            payload.approvals = approvalRepository.findAll();
            payload.deployments = deploymentRepository.findAll();
            return payload;
        });

        byte[] rawJsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot);
        boolean encrypted = encryptionPassphrase != null && !encryptionPassphrase.isBlank();
        String dbFileName = encrypted ? "db-snapshot.json.enc" : "db-snapshot.json";
        byte[] storedDbBytes = encrypted ? encryptBytes(rawJsonBytes, encryptionPassphrase) : rawJsonBytes;

        Path dbSnapshotPath = backupOutputDir.resolve(dbFileName);
        Files.write(dbSnapshotPath, storedDbBytes);
        String dbSnapshotSha256 = sha256Hex(storedDbBytes);

        // 2. Copy workspace files and compute per-file SHA-256 checksums
        Path backupWorkspacesDir = backupOutputDir.resolve("workspaces");
        Files.createDirectories(backupWorkspacesDir);
        Map<String, String> workspaceChecksums = new TreeMap<>();

        if (workspaceRoot != null && Files.exists(workspaceRoot)) {
            try (Stream<Path> stream = Files.walk(workspaceRoot)) {
                List<Path> regularFiles = stream.filter(Files::isRegularFile).collect(Collectors.toList());
                for (Path file : regularFiles) {
                    String relPath = workspaceRoot.relativize(file).toString().replace('\\', '/');
                    Path targetFile = backupWorkspacesDir.resolve(relPath);
                    Files.createDirectories(targetFile.getParent());
                    byte[] content = Files.readAllBytes(file);
                    Files.write(targetFile, content);
                    workspaceChecksums.put(relPath, sha256Hex(content));
                }
            }
        }

        // 3. Build Manifest & Sampled Run Fingerprints
        BackupManifest manifest = new BackupManifest();
        manifest.backupId = backupId;
        manifest.consistentPointUtc = consistentPointUtc;
        manifest.flywaySchemaVersion = resolveCurrentFlywayVersion();
        manifest.encrypted = encrypted;
        manifest.encryptionAlgorithm = encrypted ? "AES-256-GCM/PBKDF2WithHmacSHA256" : "NONE";
        manifest.dbSnapshotFileName = dbFileName;
        manifest.dbSnapshotSha256 = dbSnapshotSha256;

        manifest.tableCounts.put("projects", snapshot.projects.size());
        manifest.tableCounts.put("workspaces", snapshot.workspaces.size());
        manifest.tableCounts.put("workflow_runs", snapshot.workflowRuns.size());
        manifest.tableCounts.put("step_runs", snapshot.stepRuns.size());
        manifest.tableCounts.put("run_events", snapshot.runEvents.size());
        manifest.tableCounts.put("artifacts", snapshot.artifacts.size());
        manifest.tableCounts.put("approvals", snapshot.approvals.size());
        manifest.tableCounts.put("deployments", snapshot.deployments.size());
        manifest.workspaceFileChecksums.putAll(workspaceChecksums);

        for (WorkflowRunEntity run : snapshot.workflowRuns) {
            long stepCount = snapshot.stepRuns.stream().filter(s -> run.getId().equals(s.getRunId())).count();
            long eventCount = snapshot.runEvents.stream().filter(e -> run.getId().equals(e.getRunId())).count();
            long artifactCount = snapshot.artifacts.stream().filter(a -> run.getId().equals(a.getRunId())).count();
            String fingerprint = sha256Hex((run.getId() + "|" + run.getProjectId() + "|" + run.getStatus()
                    + "|steps=" + stepCount + "|events=" + eventCount + "|artifacts=" + artifactCount)
                    .getBytes(StandardCharsets.UTF_8));
            manifest.sampledRunFingerprints.put(run.getId(), fingerprint);
        }

        manifest.backupDurationMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);

        Path manifestPath = backupOutputDir.resolve("backup-manifest.json");
        Files.writeString(manifestPath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest), StandardCharsets.UTF_8);

        // 4. Write standard SHA256SUMS file
        StringBuilder shaSums = new StringBuilder();
        shaSums.append(dbSnapshotSha256).append("  ").append(dbFileName).append("\n");
        for (Map.Entry<String, String> entry : workspaceChecksums.entrySet()) {
            shaSums.append(entry.getValue()).append("  workspaces/").append(entry.getKey()).append("\n");
        }
        Files.writeString(backupOutputDir.resolve("SHA256SUMS"), shaSums.toString(), StandardCharsets.UTF_8);

        log.info("Created consistent backup [{}] at [{}] in {}ms (encrypted={}, workspaceFiles={})",
                backupId, backupOutputDir, manifest.backupDurationMs, encrypted, workspaceChecksums.size());
        return manifest;
    }

    public BackupManifest verifyBackupIntegrity(Path backupDir, String encryptionPassphrase) throws Exception {
        if (backupDir == null || !Files.exists(backupDir)) {
            throw new IllegalStateException("Backup directory does not exist: " + backupDir);
        }
        Path manifestPath = backupDir.resolve("backup-manifest.json");
        Path sha256SumsPath = backupDir.resolve("SHA256SUMS");
        if (!Files.exists(manifestPath) || !Files.exists(sha256SumsPath)) {
            throw new IllegalStateException("Backup manifest or SHA256SUMS missing in: " + backupDir);
        }

        BackupManifest manifest = objectMapper.readValue(Files.readString(manifestPath, StandardCharsets.UTF_8), BackupManifest.class);
        Path dbSnapshotPath = backupDir.resolve(manifest.dbSnapshotFileName);
        if (!Files.exists(dbSnapshotPath)) {
            throw new IllegalStateException("Database snapshot file missing: " + dbSnapshotPath);
        }

        byte[] storedDbBytes = Files.readAllBytes(dbSnapshotPath);
        String actualDbSha256 = sha256Hex(storedDbBytes);
        if (!actualDbSha256.equalsIgnoreCase(manifest.dbSnapshotSha256)) {
            throw new IllegalStateException("Checksum mismatch on database snapshot file: expected="
                    + manifest.dbSnapshotSha256 + ", actual=" + actualDbSha256);
        }

        // Verify decryption if encrypted
        if (manifest.encrypted) {
            if (encryptionPassphrase == null || encryptionPassphrase.isBlank()) {
                throw new IllegalStateException("Encryption passphrase required to verify encrypted backup");
            }
            try {
                decryptBytes(storedDbBytes, encryptionPassphrase);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt database snapshot (invalid passphrase or tampered ciphertext)", e);
            }
        }

        // Verify every workspace file checksum and path containment
        Path backupWorkspacesDir = backupDir.resolve("workspaces").toAbsolutePath().normalize();
        for (Map.Entry<String, String> entry : manifest.workspaceFileChecksums.entrySet()) {
            Path file = backupWorkspacesDir.resolve(entry.getKey()).normalize();
            if (!file.startsWith(backupWorkspacesDir)) {
                throw new IllegalStateException("Path traversal detected in backup manifest entry: " + entry.getKey());
            }
            if (!Files.exists(file)) {
                throw new IllegalStateException("Backed-up workspace file missing: " + entry.getKey());
            }
            String actualHash = sha256Hex(Files.readAllBytes(file));
            if (!actualHash.equalsIgnoreCase(entry.getValue())) {
                throw new IllegalStateException("Workspace file checksum mismatch for [" + entry.getKey()
                        + "]: expected=" + entry.getValue() + ", actual=" + actualHash);
            }
        }

        // Cross-check SHA256SUMS file contents against manifest and disk files
        List<String> sumLines = Files.readAllLines(sha256SumsPath, StandardCharsets.UTF_8);
        boolean dbSumFound = false;
        for (String rawLine : sumLines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+", 2);
            if (parts.length < 2) {
                throw new IllegalStateException("Malformed line in SHA256SUMS: " + line);
            }
            String expectedHash = parts[0].trim();
            String relFile = parts[1].trim();
            Path targetPath = backupDir.toAbsolutePath().normalize().resolve(relFile).normalize();
            if (!targetPath.startsWith(backupDir.toAbsolutePath().normalize()) || !Files.exists(targetPath)) {
                throw new IllegalStateException("SHA256SUMS references missing or escaping file: " + relFile);
            }
            String diskHash = sha256Hex(Files.readAllBytes(targetPath));
            if (!diskHash.equalsIgnoreCase(expectedHash)) {
                throw new IllegalStateException("SHA256SUMS checksum mismatch for [" + relFile + "]: expected=" + expectedHash + ", actual=" + diskHash);
            }
            if (relFile.equals(manifest.dbSnapshotFileName)) {
                dbSumFound = true;
            }
        }
        if (!dbSumFound) {
            throw new IllegalStateException("SHA256SUMS is missing entry for database snapshot file: " + manifest.dbSnapshotFileName);
        }

        return manifest;
    }

    public RestoreVerificationReport restoreToCleanEnvironment(
            Path backupDir,
            Path targetWorkspaceRoot,
            String encryptionPassphrase) throws Exception {
        long startNs = System.nanoTime();

        // 1. Pre-flight checksum & integrity verification
        BackupManifest manifest = verifyBackupIntegrity(backupDir, encryptionPassphrase);

        // 2. Decode DB snapshot
        Path dbSnapshotPath = backupDir.resolve(manifest.dbSnapshotFileName);
        byte[] storedDbBytes = Files.readAllBytes(dbSnapshotPath);
        byte[] rawJsonBytes = manifest.encrypted
                ? decryptBytes(storedDbBytes, encryptionPassphrase)
                : storedDbBytes;

        DatabaseSnapshotPayload snapshot = objectMapper.readValue(rawJsonBytes, DatabaseSnapshotPayload.class);

        // 3. Restore database records inside transaction in FK-safe order
        transactionTemplate.executeWithoutResult(status -> {
            for (ProjectEntity project : snapshot.projects) {
                projectRepository.save(project);
            }
            projectRepository.flush();

            for (WorkspaceEntity ws : snapshot.workspaces) {
                workspaceRepository.save(ws);
            }
            workspaceRepository.flush();

            for (WorkflowRunEntity run : snapshot.workflowRuns) {
                workflowRunRepository.save(run);
            }
            workflowRunRepository.flush();

            for (StepRunEntity step : snapshot.stepRuns) {
                stepRunRepository.save(step);
            }
            stepRunRepository.flush();

            for (RunEventEntity event : snapshot.runEvents) {
                if (!runEventRepository.existsById(event.getId())) {
                    runEventRepository.save(event);
                }
            }
            runEventRepository.flush();

            for (ArtifactEntity artifact : snapshot.artifacts) {
                artifactRepository.save(artifact);
            }
            artifactRepository.flush();

            for (ApprovalEntity approval : snapshot.approvals) {
                approvalRepository.save(approval);
            }
            approvalRepository.flush();

            for (DeploymentEntity deployment : snapshot.deployments) {
                deploymentRepository.save(deployment);
            }
            deploymentRepository.flush();
        });

        // 4. Restore physical workspace files and verify checksums on target disk
        int restoredFilesCount = 0;
        if (targetWorkspaceRoot != null) {
            Path normalizedTargetRoot = targetWorkspaceRoot.toAbsolutePath().normalize();
            Files.createDirectories(normalizedTargetRoot);
            Path backupWorkspacesDir = backupDir.resolve("workspaces").toAbsolutePath().normalize();
            for (Map.Entry<String, String> entry : manifest.workspaceFileChecksums.entrySet()) {
                Path srcFile = backupWorkspacesDir.resolve(entry.getKey()).normalize();
                Path destFile = normalizedTargetRoot.resolve(entry.getKey()).normalize();
                if (!srcFile.startsWith(backupWorkspacesDir) || !destFile.startsWith(normalizedTargetRoot)) {
                    throw new IllegalStateException("Path traversal detected while restoring workspace file: " + entry.getKey());
                }
                Files.createDirectories(destFile.getParent());
                byte[] bytes = Files.readAllBytes(srcFile);
                Files.write(destFile, bytes);

                String restoredHash = sha256Hex(Files.readAllBytes(destFile));
                if (!restoredHash.equalsIgnoreCase(entry.getValue())) {
                    throw new IllegalStateException("Post-restore disk checksum mismatch for: " + entry.getKey());
                }
                restoredFilesCount++;
            }
        }

        // 5. Verify Flyway schema version & Sampled Run fingerprints against live database
        String currentFlywayVersion = resolveCurrentFlywayVersion();
        boolean schemaVersionVerified = Objects.equals(manifest.flywaySchemaVersion, currentFlywayVersion);

        boolean sampledRunsVerified = true;
        String mismatchDetails = null;
        for (Map.Entry<String, String> sample : manifest.sampledRunFingerprints.entrySet()) {
            String runId = sample.getKey();
            WorkflowRunEntity restoredRun = workflowRunRepository.findById(runId).orElse(null);
            if (restoredRun == null) {
                sampledRunsVerified = false;
                mismatchDetails = "Run not found in database: " + runId;
                break;
            }
            long stepCount = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId).size();
            long eventCount = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId).size();
            long artifactCount = artifactRepository.findByRunId(runId).size();
            String actualFingerprint = sha256Hex((restoredRun.getId() + "|" + restoredRun.getProjectId() + "|" + restoredRun.getStatus()
                    + "|steps=" + stepCount + "|events=" + eventCount + "|artifacts=" + artifactCount)
                    .getBytes(StandardCharsets.UTF_8));
            if (!actualFingerprint.equalsIgnoreCase(sample.getValue())) {
                sampledRunsVerified = false;
                mismatchDetails = String.format("Fingerprint mismatch for run [%s]: expected [%s], actual [%s] (status=%s, steps=%d, events=%d, artifacts=%d)",
                        runId, sample.getValue(), actualFingerprint, restoredRun.getStatus(), stepCount, eventCount, artifactCount);
                log.warn(mismatchDetails);
                break;
            }
        }

        if (!sampledRunsVerified) {
            throw new IllegalStateException("Post-restore sampled Run verification failed! " + (mismatchDetails != null ? mismatchDetails : ""));
        }

        long restoreDurationMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
        return new RestoreVerificationReport(
                manifest.backupId,
                true,
                schemaVersionVerified,
                sampledRunsVerified,
                snapshot.projects.size(),
                snapshot.workflowRuns.size(),
                snapshot.stepRuns.size(),
                snapshot.runEvents.size(),
                snapshot.artifacts.size(),
                snapshot.deployments.size(),
                restoredFilesCount,
                restoreDurationMs
        );
    }

    /**
     * Enforces retention policy on backup subdirectories under backupRoot.
     * Always keeps at least minKeepCount newest valid backups, and only deletes backups older than maxAge.
     */
    public List<String> applyRetentionPolicy(Path backupRoot, int minKeepCount, Duration maxAge) throws IOException {
        if (backupRoot == null || !Files.exists(backupRoot)) {
            return Collections.emptyList();
        }
        List<Path> backupDirs;
        try (Stream<Path> children = Files.list(backupRoot)) {
            backupDirs = children
                    .filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("backup-manifest.json")))
                    .sorted((a, b) -> {
                        try {
                            return Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a));
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .collect(Collectors.toList());
        }

        List<String> pruned = new ArrayList<>();
        Instant cutoff = Instant.now().minus(maxAge);
        int safeMinKeep = Math.max(1, minKeepCount);

        for (int i = safeMinKeep; i < backupDirs.size(); i++) {
            Path candidate = backupDirs.get(i);
            FileTime mtime = Files.getLastModifiedTime(candidate);
            if (mtime.toInstant().isBefore(cutoff)) {
                FileSystemUtils.deleteRecursively(candidate);
                pruned.add(candidate.getFileName().toString());
            }
        }
        return pruned;
    }

    /**
     * Phase E Work Item 9: Safe Database-Reconciled Workspace Cleanup.
     * Strictly forbids deleting workspaces solely by directory mtime without DB correlation.
     * A workspace directory under workspaceRoot is ONLY eligible for cleanup if:
     * 1. Its corresponding Workspace & Project record is explicitly marked status='ARCHIVED' in the DB;
     * 2. No WorkflowRun for that project is in RUNNING, QUEUED, or WAITING_APPROVAL status;
     * 3. No active lock is held on the workspace in WorkspaceLockManager / workspace_locks;
     * 4. Its directory mtime is older than minArchivedAge.
     */
    public List<String> cleanupArchivedWorkspacesWithDbReconciliation(Path workspaceRoot, Duration minArchivedAge) throws IOException {
        if (workspaceRoot == null || !Files.exists(workspaceRoot)) {
            return Collections.emptyList();
        }
        List<String> cleaned = new ArrayList<>();
        Instant cutoff = Instant.now().minus(minArchivedAge);

        List<Path> candidates;
        try (Stream<Path> children = Files.list(workspaceRoot)) {
            candidates = children.filter(Files::isDirectory).collect(Collectors.toList());
        }

        for (Path dir : candidates) {
            String dirName = dir.getFileName().toString();
            FileTime lastModified = Files.getLastModifiedTime(dir);
            if (!lastModified.toInstant().isBefore(cutoff)) {
                continue;
            }

            // Look up workspace by id or relative_root
            WorkspaceEntity ws = workspaceRepository.findById(dirName).orElseGet(() ->
                    workspaceRepository.findAll().stream()
                            .filter(w -> dirName.equals(w.getRelativeRoot()) || dirName.equals(w.getId()))
                            .findFirst()
                            .orElse(null)
            );

            // Never delete unassociated or active project workspaces!
            if (ws == null) {
                log.info("Skipping workspace directory [{}] during cleanup: no DB workspace record associated", dirName);
                continue;
            }

            ProjectEntity project = projectRepository.findById(ws.getProjectId()).orElse(null);
            if (project == null || !"ARCHIVED".equalsIgnoreCase(project.getStatus())) {
                log.info("Skipping workspace directory [{}] during cleanup: project [{}] is not ARCHIVED (status={})",
                        dirName, ws.getProjectId(), project != null ? project.getStatus() : "MISSING");
                continue;
            }

            // Verify no active workflow runs exist for this project
            boolean hasActiveRuns = workflowRunRepository.findByProjectIdOrderByCreatedAtDesc(project.getId())
                    .stream()
                    .anyMatch(r -> List.of("RUNNING", "QUEUED", "PENDING", "WAITING_APPROVAL")
                            .contains(r.getStatus().toUpperCase(Locale.ROOT)));
            if (hasActiveRuns) {
                log.info("Skipping workspace directory [{}] during cleanup: project [{}] has active runs", dirName, project.getId());
                continue;
            }

            // Verify no active lock is held across physical path, directory name, workspaceId, or projectId
            boolean lockHeld = Stream.of(dir.toString(), dirName, ws.getId(), ws.getProjectId())
                    .filter(Objects::nonNull)
                    .anyMatch(k -> workspaceLockManager.isLocked(k)
                            || workspaceLockManager.getDatabaseLockOwner(k).isPresent());
            if (lockHeld) {
                log.info("Skipping workspace directory [{}] during cleanup: workspace lock is active", dirName);
                continue;
            }

            org.eclipse.jgit.lib.RepositoryCache.clear();
            FileSystemUtils.deleteRecursively(dir);
            cleaned.add(dirName);
        }

        return cleaned;
    }

    private String resolveCurrentFlywayVersion() {
        if (flyway == null) {
            return "unknown";
        }
        MigrationInfo current = flyway.info().current();
        return current != null && current.getVersion() != null ? current.getVersion().getVersion() : "baseline";
    }

    private byte[] encryptBytes(byte[] plainBytes, String passphrase) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(salt);
        random.nextBytes(iv);

        SecretKey key = deriveAesKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] cipherText = cipher.doFinal(plainBytes);

        byte[] combined = new byte[SALT_LENGTH + GCM_IV_LENGTH + cipherText.length];
        System.arraycopy(salt, 0, combined, 0, SALT_LENGTH);
        System.arraycopy(iv, 0, combined, SALT_LENGTH, GCM_IV_LENGTH);
        System.arraycopy(cipherText, 0, combined, SALT_LENGTH + GCM_IV_LENGTH, cipherText.length);
        return combined;
    }

    private byte[] decryptBytes(byte[] combined, String passphrase) throws Exception {
        if (combined.length < SALT_LENGTH + GCM_IV_LENGTH + 16) {
            throw new IllegalArgumentException("Ciphertext too short");
        }
        byte[] salt = Arrays.copyOfRange(combined, 0, SALT_LENGTH);
        byte[] iv = Arrays.copyOfRange(combined, SALT_LENGTH, SALT_LENGTH + GCM_IV_LENGTH);
        byte[] cipherText = Arrays.copyOfRange(combined, SALT_LENGTH + GCM_IV_LENGTH, combined.length);

        SecretKey key = deriveAesKey(passphrase, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(cipherText);
    }

    private SecretKey deriveAesKey(String passphrase, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute SHA-256", e);
        }
    }
}
