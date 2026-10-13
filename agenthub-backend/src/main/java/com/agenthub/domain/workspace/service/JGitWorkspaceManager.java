package com.agenthub.domain.workspace.service;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.project.domain.source.GitSourceConfig;
import com.agenthub.project.domain.source.GitSourceType;
import com.agenthub.project.dto.onboarding.EnvironmentScanResultView;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class JGitWorkspaceManager {

    private static final Logger log = LoggerFactory.getLogger(JGitWorkspaceManager.class);
    private static final String DEFAULT_AUTHOR = "AgentHub";
    private static final String DEFAULT_EMAIL = "bot@agenthub.local";

    public static class StepSnapshotResult {
        private final String commitHash;
        private final String tagName;
        private final List<String> changedFiles;
        private final Map<String, String> fileChecksums;
        private final String author;
        private final long timestamp;

        public StepSnapshotResult(String commitHash, String tagName, List<String> changedFiles,
                                  Map<String, String> fileChecksums, String author, long timestamp) {
            this.commitHash = commitHash;
            this.tagName = tagName;
            this.changedFiles = changedFiles;
            this.fileChecksums = fileChecksums;
            this.author = author;
            this.timestamp = timestamp;
        }

        public String getCommitHash() { return commitHash; }
        public String getTagName() { return tagName; }
        public List<String> getChangedFiles() { return changedFiles; }
        public Map<String, String> getFileChecksums() { return fileChecksums; }
        public String getAuthor() { return author; }
        public long getTimestamp() { return timestamp; }
    }

    public void initWorkspace(File workspaceDir) throws Exception {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        File gitDir = new File(workspaceDir, ".git");
        if (!gitDir.exists()) {
            try (Git git = Git.init().setDirectory(workspaceDir).call()) {
                try {
                    File readme = new File(workspaceDir, "README.md");
                    if (!readme.exists()) {
                        Files.writeString(readme.toPath(), "# AgentHub Project Workspace\nInitial Baseline.", StandardCharsets.UTF_8);
                    }
                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Initial project baseline commit").setAuthor(DEFAULT_AUTHOR, DEFAULT_EMAIL).call();
                    log.info("Initialized Git workspace baseline at: {}", workspaceDir.getAbsolutePath());
                } finally {
                    closeRepositoryQuietly(git, workspaceDir);
                }
            }
        }
    }

    public String createBaseline(File workspaceDir, String runId, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : DEFAULT_EMAIL;
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();

                Status status = git.status().call();
                RevCommit commit;
                if (!status.isClean()) {
                    commit = git.commit()
                            .setMessage("Baseline commit for Run " + runId)
                            .setAuthor(DEFAULT_AUTHOR, author)
                            .call();
                } else {
                    Repository repo = git.getRepository();
                    ObjectId head = repo.resolve("HEAD");
                    try (RevWalk revWalk = new RevWalk(repo)) {
                        commit = revWalk.parseCommit(head);
                    }
                }

                String tagName = "tag-run-baseline-" + runId;
                try {
                    git.tag().setName(tagName).setObjectId(commit).call();
                } catch (Exception e) {
                    log.debug("Tag {} may already exist: {}", tagName, e.getMessage());
                }

                return commit.getName();
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public StepSnapshotResult createStepSnapshot(File workspaceDir, String runId, String stepRunId,
                                                String message, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : DEFAULT_EMAIL;

                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();

                Status status = git.status().call();
                List<String> changedFiles = new ArrayList<>();
                changedFiles.addAll(status.getUntracked());
                changedFiles.addAll(status.getAdded());
                changedFiles.addAll(status.getChanged());
                changedFiles.addAll(status.getModified());
                changedFiles.addAll(status.getMissing());
                changedFiles.addAll(status.getRemoved());

                RevCommit commit;
                if (!status.isClean()) {
                    String commitMsg = String.format("[StepSnapshot] run=%s step=%s: %s",
                            runId, stepRunId, message != null ? message : "Step completed");
                    commit = git.commit()
                            .setMessage(commitMsg)
                            .setAuthor(DEFAULT_AUTHOR, author)
                            .call();
                } else {
                    Repository repo = git.getRepository();
                    ObjectId head = repo.resolve("HEAD");
                    try (RevWalk revWalk = new RevWalk(repo)) {
                        commit = revWalk.parseCommit(head);
                    }
                }

                String tagName = "snapshot-" + stepRunId;
                try {
                    git.tag().setName(tagName).setObjectId(commit).call();
                } catch (Exception e) {
                    log.debug("Tag {} already exists: {}", tagName, e.getMessage());
                }

                Map<String, String> checksums = new HashMap<>();
                for (String fileRel : changedFiles) {
                    File f = new File(workspaceDir, fileRel);
                    if (f.exists() && f.isFile()) {
                        checksums.put(fileRel, calculateFileChecksum(f.toPath()));
                    }
                }

                return new StepSnapshotResult(
                        commit.getName(),
                        tagName,
                        changedFiles,
                        checksums,
                        author,
                        System.currentTimeMillis()
                );
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void commitSnapshot(File workspaceDir, String message) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.commit().setMessage(message).setAuthor(DEFAULT_AUTHOR, DEFAULT_EMAIL).call();
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public StructuredDiff computeStructuredDiff(File workspaceDir) throws Exception {
        initWorkspace(workspaceDir);
        List<FileDiffEntry> diffEntries = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;
        String baselineHash = "";
        String headHash = "";

        try (Git git = Git.open(workspaceDir)) {
            Repository repo = git.getRepository();
            try (ObjectReader reader = repo.newObjectReader()) {

            ObjectId headId = repo.resolve("HEAD");
            if (headId != null) {
                headHash = headId.getName();
                baselineHash = headHash;
            }

            ObjectId treeId = repo.resolve("HEAD^{tree}");
            if (treeId == null) {
                return new StructuredDiff(workspaceDir.getName(), baselineHash, headHash, 0, 0, 0, diffEntries);
            }

            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            oldTreeIter.reset(reader, treeId);

            FileTreeIterator newTreeIter = new FileTreeIterator(repo);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (DiffFormatter df = new DiffFormatter(out)) {
                df.setRepository(repo);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);

                List<DiffEntry> entries = df.scan(oldTreeIter, newTreeIter);
                if (entries.isEmpty()) {
                    ObjectId parentCommitId = repo.resolve("HEAD~1");
                    ObjectId parentTreeId = repo.resolve("HEAD~1^{tree}");
                    if (parentCommitId != null && parentTreeId != null) {
                        baselineHash = parentCommitId.getName();
                        oldTreeIter.reset(reader, parentTreeId);
                        newTreeIter = new FileTreeIterator(repo);
                        entries = df.scan(oldTreeIter, newTreeIter);
                    }
                }
                for (DiffEntry entry : entries) {
                    out.reset();
                    df.format(entry);
                    String diffText = out.toString(StandardCharsets.UTF_8);

                    int added = 0;
                    int deleted = 0;
                    for (Edit edit : df.toFileHeader(entry).toEditList()) {
                        deleted += edit.getEndA() - edit.getBeginA();
                        added += edit.getEndB() - edit.getBeginB();
                    }

                    FileDiffEntry.ChangeType changeType = switch (entry.getChangeType()) {
                        case ADD -> FileDiffEntry.ChangeType.ADD;
                        case DELETE -> FileDiffEntry.ChangeType.DELETE;
                        case RENAME -> FileDiffEntry.ChangeType.RENAME;
                        case COPY -> FileDiffEntry.ChangeType.COPY;
                        default -> FileDiffEntry.ChangeType.MODIFY;
                    };

                    String targetRelPath = entry.getNewPath() != null ? entry.getNewPath() : entry.getOldPath();
                    File targetFile = new File(workspaceDir, targetRelPath);

                    boolean isBinary = isBinaryFile(targetFile) || diffText.contains("Binary files differ");
                    boolean hasConflict = checkFileConflict(targetFile);
                    String checksum = (targetFile.exists() && targetFile.isFile()) ? calculateFileChecksum(targetFile.toPath()) : "";

                    diffEntries.add(new FileDiffEntry(
                            entry.getOldPath(),
                            entry.getNewPath(),
                            changeType,
                            diffText,
                            added,
                            deleted,
                            isBinary,
                            entry.getChangeType() == DiffEntry.ChangeType.RENAME,
                            hasConflict,
                            FileDiffEntry.ReviewStatus.PENDING,
                            checksum
                    ));

                    totalAdded += added;
                    totalDeleted += deleted;
                }
            }
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }

        return new StructuredDiff(
                workspaceDir.getName(),
                baselineHash,
                headHash,
                diffEntries.size(),
                totalAdded,
                totalDeleted,
                diffEntries
        );
    }

    public StructuredDiff computeStructuredDiffBetweenCommits(File workspaceDir, String fromCommitHash, String toCommitHash) throws Exception {
        if (fromCommitHash == null || fromCommitHash.isBlank()
                || toCommitHash == null || toCommitHash.isBlank()
                || fromCommitHash.equals(toCommitHash)) {
            return computeStructuredDiff(workspaceDir);
        }
        initWorkspace(workspaceDir);
        List<FileDiffEntry> diffEntries = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;

        try (Git git = Git.open(workspaceDir)) {
            Repository repo = git.getRepository();
            try (ObjectReader reader = repo.newObjectReader()) {
                ObjectId oldTreeId = repo.resolve(fromCommitHash + "^{tree}");
                ObjectId newTreeId = repo.resolve(toCommitHash + "^{tree}");
                if (oldTreeId == null || newTreeId == null) {
                    return computeStructuredDiff(workspaceDir);
                }

                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, oldTreeId);
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, newTreeId);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try (DiffFormatter df = new DiffFormatter(out)) {
                    df.setRepository(repo);
                    df.setDiffComparator(RawTextComparator.DEFAULT);
                    df.setDetectRenames(true);

                    List<DiffEntry> entries = df.scan(oldTreeIter, newTreeIter);
                    if (entries.isEmpty()) {
                        return computeStructuredDiff(workspaceDir);
                    }
                    for (DiffEntry entry : entries) {
                        out.reset();
                        df.format(entry);
                        String diffText = out.toString(StandardCharsets.UTF_8);

                        int added = 0;
                        int deleted = 0;
                        for (Edit edit : df.toFileHeader(entry).toEditList()) {
                            deleted += edit.getEndA() - edit.getBeginA();
                            added += edit.getEndB() - edit.getBeginB();
                        }

                        FileDiffEntry.ChangeType changeType = switch (entry.getChangeType()) {
                            case ADD -> FileDiffEntry.ChangeType.ADD;
                            case DELETE -> FileDiffEntry.ChangeType.DELETE;
                            case RENAME -> FileDiffEntry.ChangeType.RENAME;
                            case COPY -> FileDiffEntry.ChangeType.COPY;
                            default -> FileDiffEntry.ChangeType.MODIFY;
                        };

                        String targetRelPath = entry.getNewPath() != null && !"/dev/null".equals(entry.getNewPath())
                                ? entry.getNewPath()
                                : entry.getOldPath();
                        byte[] committedBytes = (entry.getChangeType() != DiffEntry.ChangeType.DELETE && targetRelPath != null)
                                ? readFileAtCommit(repo, toCommitHash, targetRelPath)
                                : null;
                        boolean isBinary = diffText.contains("Binary files differ");
                        boolean hasConflict = false;
                        String checksum = "";
                        if (committedBytes != null) {
                            checksum = calculateBytesChecksum(committedBytes);
                            String text = new String(committedBytes, StandardCharsets.UTF_8);
                            hasConflict = text.contains("<<<<<<< ") && text.contains("=======");
                        }

                        diffEntries.add(new FileDiffEntry(
                                entry.getOldPath(),
                                entry.getNewPath(),
                                changeType,
                                diffText,
                                added,
                                deleted,
                                isBinary,
                                entry.getChangeType() == DiffEntry.ChangeType.RENAME,
                                hasConflict,
                                FileDiffEntry.ReviewStatus.PENDING,
                                checksum
                        ));

                        totalAdded += added;
                        totalDeleted += deleted;
                    }
                }
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }

        return new StructuredDiff(
                workspaceDir.getName(),
                fromCommitHash,
                toCommitHash,
                diffEntries.size(),
                totalAdded,
                totalDeleted,
                diffEntries
        );
    }

    public List<FileDiffEntry> computeWorkspaceDiff(File workspaceDir) throws Exception {
        return computeStructuredDiff(workspaceDir).getEntries();
    }

    public void revertStepFiles(File workspaceDir, String baselineCommitHash, List<String> filePaths, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId baselineId = repo.resolve(baselineCommitHash);
                if (baselineId == null || !repo.hasObject(baselineId)) {
                    throw new IllegalArgumentException("Baseline commit hash not found in repository: " + baselineCommitHash);
                }

                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : DEFAULT_EMAIL;
                Path wsPath = workspaceDir.toPath().toAbsolutePath().normalize();

                if (filePaths != null) {
                    for (String relPath : filePaths) {
                        if (relPath == null || relPath.isBlank() || relPath.contains(".git")) continue;
                        String normalizedPath = relPath.replace('\\', '/').replaceAll("^/+", "");
                        Path targetPath = wsPath.resolve(normalizedPath).normalize();
                        if (!targetPath.startsWith(wsPath)) {
                            log.warn("Path traversal detected in revertStepFiles, skipping: {}", relPath);
                            continue;
                        }

                        byte[] baselineContent = readFileAtCommit(repo, baselineCommitHash, normalizedPath);
                        File fileOnDisk = targetPath.toFile();

                        if (baselineContent != null) {
                            if (fileOnDisk.getParentFile() != null && !fileOnDisk.getParentFile().exists()) {
                                fileOnDisk.getParentFile().mkdirs();
                            }
                            Files.write(fileOnDisk.toPath(), baselineContent);
                            log.info("Restored file [{}] to baseline commit [{}]", normalizedPath, baselineCommitHash);
                        } else {
                            if (fileOnDisk.exists()) {
                                fileOnDisk.delete();
                                log.info("Deleted newly added file [{}] on revert", normalizedPath);
                            }
                        }
                    }
                }

                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();

                Status status = git.status().call();
                if (!status.isClean()) {
                    git.commit()
                            .setMessage("[Revert] Rollback step files to baseline " + baselineCommitHash)
                            .setAuthor(DEFAULT_AUTHOR, author)
                            .call();
                    log.info("Committed revert to baseline {} in workspace {}", baselineCommitHash, workspaceDir.getAbsolutePath());
                }
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public List<String> getChangedFilesInCommit(File workspaceDir, String commitHash) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId commitId = repo.resolve(commitHash);
                if (commitId == null) {
                    return Collections.emptyList();
                }
                try (RevWalk rw = new RevWalk(repo)) {
                    RevCommit commit = rw.parseCommit(commitId);
                    RevCommit parent = commit.getParentCount() > 0 ? rw.parseCommit(commit.getParent(0).getId()) : null;
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (DiffFormatter df = new DiffFormatter(out)) {
                        df.setRepository(repo);
                        List<DiffEntry> diffs = (parent != null)
                                ? df.scan(parent.getTree(), commit.getTree())
                                : df.scan(null, commit.getTree());
                        List<String> files = new ArrayList<>();
                        for (DiffEntry d : diffs) {
                            String p = d.getNewPath();
                            if (p != null && !"/dev/null".equals(p)) {
                                files.add(p);
                            } else if (d.getOldPath() != null) {
                                files.add(d.getOldPath());
                            }
                        }
                        return files;
                    }
                }
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public byte[] readFileAtCommit(Repository repo, String commitHash, String relativePath) throws IOException {
        ObjectId commitId = repo.resolve(commitHash);
        if (commitId == null) {
            return null;
        }
        try (RevWalk revWalk = new RevWalk(repo)) {
            RevCommit commit = revWalk.parseCommit(commitId);
            RevTree tree = commit.getTree();
            String normalizedPath = relativePath.replace('\\', '/').replaceAll("^/+", "");
            try (TreeWalk treeWalk = TreeWalk.forPath(repo, normalizedPath, tree)) {
                if (treeWalk != null) {
                    ObjectId blobId = treeWalk.getObjectId(0);
                    return repo.open(blobId).getBytes();
                }
            }
        }
        return null;
    }

    public String cloneOrInitFromSource(File workspaceDir, GitSourceConfig sourceConfig, String branchName, String authorEmail) throws Exception {
        if (sourceConfig == null) {
            sourceConfig = new GitSourceConfig();
        }
        sourceConfig.validate();

        String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : DEFAULT_EMAIL;
        String effectiveBranch = (branchName != null && !branchName.isBlank()) ? branchName : "main";

        if (sourceConfig.getSourceType() == GitSourceType.REMOTE_GIT) {
            String repoUrl = sourceConfig.getRepoUrl();
            log.info("Cloning remote Git repository from [{}] into [{}]", SecretMasker.maskUrl(repoUrl), workspaceDir.getAbsolutePath());
            try {
                var cloneCmd = Git.cloneRepository()
                        .setURI(repoUrl)
                        .setDirectory(workspaceDir)
                        .setBranch(sourceConfig.getDefaultBranch());

                String secret = SecretMasker.resolveSecret(sourceConfig.getSecretRef(), null, "GITHUB_TOKEN");
                if (secret != null && !secret.isBlank()) {
                    cloneCmd.setCredentialsProvider(new UsernamePasswordCredentialsProvider(secret, ""));
                }

                try (Git git = cloneCmd.call()) {
                    try {
                        if (!effectiveBranch.equals(sourceConfig.getDefaultBranch())) {
                            git.checkout().setCreateBranch(true).setName(effectiveBranch).call();
                        }
                        ObjectId head = git.getRepository().resolve("HEAD");
                        return head != null ? head.getName() : "";
                    } finally {
                        closeRepositoryQuietly(git, workspaceDir);
                    }
                }
            } catch (Exception e) {
                String maskedErr = SecretMasker.maskInText(e.getMessage());
                log.error("Failed to clone remote repository [{}]: {}", SecretMasker.maskUrl(repoUrl), maskedErr);
                throw new BusinessException(ErrorCode.ONBOARDING_FAILED, "Remote Git clone failed: " + maskedErr);
            }
        } else {
            // LOCAL_DIR
            initWorkspace(workspaceDir);
            try (Git git = Git.open(workspaceDir)) {
                try {
                    String location = sourceConfig.getLocation();
                    if (location != null && !location.isBlank()) {
                        File srcDir = new File(location);
                        if (!srcDir.isAbsolute()) {
                            File controlledDir = new File("./data/controlled-sources", location);
                            if (controlledDir.exists() && controlledDir.isDirectory()) {
                                copyDirectoryContents(controlledDir, workspaceDir);
                            }
                        }
                    }

                    git.add().addFilepattern(".").call();
                    Status status = git.status().call();
                    RevCommit commit;
                    if (!status.isClean()) {
                        commit = git.commit()
                                .setMessage("Initial onboarded baseline from " + sourceConfig.getMaskedIdentifier())
                                .setAuthor(DEFAULT_AUTHOR, author)
                                .call();
                    } else {
                        ObjectId head = git.getRepository().resolve("HEAD");
                        try (RevWalk rw = new RevWalk(git.getRepository())) {
                            commit = rw.parseCommit(head);
                        }
                    }

                    if (!"main".equals(effectiveBranch) && !"master".equals(effectiveBranch)) {
                        try {
                            git.checkout().setCreateBranch(true).setName(effectiveBranch).call();
                        } catch (Exception ignored) {}
                    }

                    String tagName = "tag-onboard-baseline-" + commit.getName().substring(0, Math.min(8, commit.getName().length()));
                    try {
                        git.tag().setName(tagName).setObjectId(commit).call();
                    } catch (Exception ignored) {}

                    return commit.getName();
                } finally {
                    closeRepositoryQuietly(git, workspaceDir);
                }
            }
        }
    }

    public EnvironmentScanResultView scanEnvironment(File workspaceDir) {
        if (workspaceDir == null || !workspaceDir.exists()) {
            return new EnvironmentScanResultView("Unknown", "Generic", "None", "None", "", "", List.of());
        }

        List<String> detectedFiles = new ArrayList<>();
        File pomFile = new File(workspaceDir, "pom.xml");
        File gradleFile = new File(workspaceDir, "build.gradle");
        File pkgFile = new File(workspaceDir, "package.json");
        File reqFile = new File(workspaceDir, "requirements.txt");
        File goFile = new File(workspaceDir, "go.mod");

        if (pomFile.exists()) detectedFiles.add("pom.xml");
        if (gradleFile.exists()) detectedFiles.add("build.gradle");
        if (pkgFile.exists()) detectedFiles.add("package.json");
        if (reqFile.exists()) detectedFiles.add("requirements.txt");
        if (goFile.exists()) detectedFiles.add("go.mod");

        if (pomFile.exists()) {
            boolean isSpring = false;
            try {
                String pomContent = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
                isSpring = pomContent.contains("spring-boot");
            } catch (Exception ignored) {}
            return new EnvironmentScanResultView(
                    "Java 17+",
                    isSpring ? "Spring Boot 3.x" : "Java Maven Project",
                    "Maven",
                    "JUnit 5",
                    "mvn clean package -DskipTests",
                    "mvn test",
                    detectedFiles
            );
        } else if (gradleFile.exists()) {
            return new EnvironmentScanResultView(
                    "Java/Kotlin",
                    "Gradle Project",
                    "Gradle",
                    "JUnit",
                    "./gradlew build -x test",
                    "./gradlew test",
                    detectedFiles
            );
        } else if (pkgFile.exists()) {
            boolean isNext = false;
            try {
                String pkgContent = Files.readString(pkgFile.toPath(), StandardCharsets.UTF_8);
                isNext = pkgContent.contains("next");
            } catch (Exception ignored) {}
            return new EnvironmentScanResultView(
                    "TypeScript / JavaScript",
                    isNext ? "Next.js 14+" : "Node.js Application",
                    "npm",
                    "Jest / Vitest",
                    "npm run build",
                    "npm test",
                    detectedFiles
            );
        } else if (reqFile.exists()) {
            return new EnvironmentScanResultView(
                    "Python 3.x",
                    "Python Application",
                    "pip",
                    "pytest",
                    "pip install -r requirements.txt",
                    "pytest",
                    detectedFiles
            );
        } else if (goFile.exists()) {
            return new EnvironmentScanResultView(
                    "Go",
                    "Go Module",
                    "go build",
                    "go test",
                    "go build ./...",
                    "go test ./...",
                    detectedFiles
            );
        }

        return new EnvironmentScanResultView(
                "General Software",
                "Standard Repository",
                "Custom",
                "Custom",
                "",
                "",
                detectedFiles
        );
    }

    public void resyncWorkspace(File workspaceDir, String baselineCommit) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                String target = (baselineCommit != null && !baselineCommit.isBlank()) ? baselineCommit : "HEAD";
                ObjectId commitId = repo.resolve(target);
                if (commitId == null) {
                    throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Baseline commit not found: " + target);
                }
                git.reset().setMode(ResetType.HARD).setRef(target).call();
                git.clean().setCleanDirectories(true).setForce(true).call();
                log.info("Resynced workspace [{}] to commit [{}]", workspaceDir.getAbsolutePath(), target);
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public String changeBaseline(File workspaceDir, String targetCommitHash) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId commitId = repo.resolve(targetCommitHash);
                if (commitId == null || !repo.hasObject(commitId)) {
                    throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Target baseline commit not found: " + targetCommitHash);
                }
                RevCommit commit;
                try (RevWalk rw = new RevWalk(repo)) {
                    commit = rw.parseCommit(commitId);
                }
                git.reset().setMode(ResetType.HARD).setRef(targetCommitHash).call();
                git.clean().setCleanDirectories(true).setForce(true).call();

                String tagName = "tag-baseline-" + targetCommitHash.substring(0, Math.min(8, targetCommitHash.length()));
                try {
                    git.tag().setName(tagName).setObjectId(commit).setForceUpdate(true).call();
                } catch (Exception ignored) {}
                log.info("Changed baseline in workspace [{}] to commit [{}]", workspaceDir.getAbsolutePath(), targetCommitHash);
                return commit.getName();
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void discardWorkspaceChanges(File workspaceDir, String baselineCommit) throws Exception {
        resyncWorkspace(workspaceDir, baselineCommit);
    }

    public String restoreSnapshot(File workspaceDir, String snapshotCommitOrTag) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId commitId = repo.resolve(snapshotCommitOrTag);
                if (commitId == null || !repo.hasObject(commitId)) {
                    throw new BusinessException(ErrorCode.WORKSPACE_GIT_ERROR, "Snapshot commit or tag not found: " + snapshotCommitOrTag);
                }
                RevCommit commit;
                try (RevWalk rw = new RevWalk(repo)) {
                    commit = rw.parseCommit(commitId);
                }
                git.reset().setMode(ResetType.HARD).setRef(snapshotCommitOrTag).call();
                git.clean().setCleanDirectories(true).setForce(true).call();
                log.info("Restored snapshot in workspace [{}] to [{}]", workspaceDir.getAbsolutePath(), snapshotCommitOrTag);
                return commit.getName();
            } finally {
                closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    private void copyDirectoryContents(File sourceDir, File targetDir) {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) return;
        if (!targetDir.exists()) targetDir.mkdirs();

        File[] files = sourceDir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName().equalsIgnoreCase(".git")) continue;
            File dest = new File(targetDir, f.getName());
            if (f.isDirectory()) {
                copyDirectoryContents(f, dest);
            } else {
                try {
                    Files.copy(f.toPath(), dest.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception ignored) {}
            }
        }
    }

    public static String calculateFileChecksum(Path path) {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return calculateBytesChecksum(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    public static String calculateBytesChecksum(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static boolean isBinaryFile(File file) {
        if (!file.exists() || file.isDirectory()) {
            return false;
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        String[] binaryExts = {".png", ".jpg", ".jpeg", ".gif", ".ico", ".pdf", ".zip", ".tar",
                ".gz", ".exe", ".dll", ".so", ".class", ".jar", ".bin", ".mp3", ".mp4", ".woff", ".woff2"};
        for (String ext : binaryExts) {
            if (name.endsWith(ext)) return true;
        }

        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[4096];
            int read = in.read(buf);
            for (int i = 0; i < read; i++) {
                if (buf[i] == 0) return true;
            }
        } catch (IOException ignored) {}
        return false;
    }

    private boolean checkFileConflict(File file) {
        if (!file.exists() || file.isDirectory()) return false;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return content.contains("<<<<<<< ") && content.contains("=======");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void closeRepositoryQuietly(Git git, File workspaceDir) {
        try {
            if (git != null) {
                if (git.getRepository() != null) {
                    if (git.getRepository().getObjectDatabase() != null) {
                        git.getRepository().getObjectDatabase().close();
                    }
                    git.getRepository().close();
                }
                git.close();
            }
        } catch (Exception ignored) {}
        try {
            org.eclipse.jgit.lib.RepositoryCache.clear();
        } catch (Exception ignored) {}
        try {
            new org.eclipse.jgit.storage.file.WindowCacheConfig().install();
        } catch (Exception ignored) {}
        if (workspaceDir != null) {
            File gitDir = new File(workspaceDir, ".git");
            if (gitDir.exists()) {
                try (var stream = Files.walk(gitDir.toPath())) {
                    stream.forEach(p -> {
                        try {
                            p.toFile().setWritable(true);
                        } catch (Exception ignored) {}
                    });
                } catch (Exception ignored) {}
            }
        }
    }
}
