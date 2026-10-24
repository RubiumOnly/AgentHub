package com.agenthub.domain.workspace.service;

import com.agenthub.agent.infrastructure.security.SecretMasker;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.jgit.JGitDiffOperations;
import com.agenthub.domain.workspace.service.jgit.JGitRevertOperations;
import com.agenthub.domain.workspace.service.jgit.JGitSnapshotOperations;
import com.agenthub.domain.workspace.service.jgit.JGitSupport;
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
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * JGit 工作区管理门面（Phase 10 R9 深模块拆分）：快照/结构化 Diff/回滚与基线/工具方法
 * 已拆分到 jgit/ 包四缝与 JGitSupport；本类保留 cloneOrInitFromSource、环境扫描与公开 API 委托。
 */
@Service
public class JGitWorkspaceManager {

    @org.springframework.beans.factory.annotation.Autowired
    private JGitSnapshotOperations snapshotOperations;

    @org.springframework.beans.factory.annotation.Autowired
    private JGitDiffOperations diffOperations;

    @org.springframework.beans.factory.annotation.Autowired
    private JGitRevertOperations revertOperations;


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


    public String cloneOrInitFromSource(File workspaceDir, GitSourceConfig sourceConfig, String branchName, String authorEmail) throws Exception {
        if (sourceConfig == null) {
            sourceConfig = new GitSourceConfig();
        }
        sourceConfig.validate();

        String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : JGitSupport.DEFAULT_EMAIL;
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
                        JGitSupport.closeRepositoryQuietly(git, workspaceDir);
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
                                JGitSupport.copyDirectoryContents(controlledDir, workspaceDir);
                            }
                        }
                    }

                    git.add().addFilepattern(".").call();
                    Status status = git.status().call();
                    RevCommit commit;
                    if (!status.isClean()) {
                        commit = git.commit()
                                .setMessage("Initial onboarded baseline from " + sourceConfig.getMaskedIdentifier())
                                .setAuthor(JGitSupport.DEFAULT_AUTHOR, author)
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
                    JGitSupport.closeRepositoryQuietly(git, workspaceDir);
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
        File submodulesFile = new File(workspaceDir, ".gitmodules");

        if (pomFile.exists()) detectedFiles.add("pom.xml");
        if (gradleFile.exists()) detectedFiles.add("build.gradle");
        if (pkgFile.exists()) detectedFiles.add("package.json");
        if (reqFile.exists()) detectedFiles.add("requirements.txt");
        if (goFile.exists()) detectedFiles.add("go.mod");
        if (submodulesFile.exists()) detectedFiles.add(".gitmodules");

        // Subdirectory check for multi-module/monorepo structures if root has no primary manifest
        if (!pomFile.exists() && !gradleFile.exists() && !pkgFile.exists() && !reqFile.exists() && !goFile.exists()) {
            File[] subDirs = workspaceDir.listFiles(f -> f.isDirectory() && !f.getName().startsWith(".") && !Files.isSymbolicLink(f.toPath()));
            if (subDirs != null) {
                for (File sub : subDirs) {
                    if (sub.getName().equalsIgnoreCase("target") || sub.getName().equalsIgnoreCase("node_modules")) continue;
                    File sPom = new File(sub, "pom.xml");
                    File sPkg = new File(sub, "package.json");
                    File sGradle = new File(sub, "build.gradle");
                    if (sPom.exists()) detectedFiles.add(sub.getName() + "/pom.xml");
                    if (sPkg.exists()) detectedFiles.add(sub.getName() + "/package.json");
                    if (sGradle.exists()) detectedFiles.add(sub.getName() + "/build.gradle");
                }
            }
        }

        String securityNotice = "安全须知：扫描结果仅供参考，系统严禁自动执行未知构建命令，需用户审查确认。";
        if (submodulesFile.exists()) {
            securityNotice += " 检测到 Git 子模块 (.gitmodules)，受控工作区默认禁用自动递归克隆以防 SSRF 攻击。";
        }

        EnvironmentScanResultView view;
        if (pomFile.exists() || detectedFiles.stream().anyMatch(f -> f.endsWith("/pom.xml"))) {
            boolean isSpring = false;
            try {
                if (pomFile.exists()) {
                    String pomContent = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
                    isSpring = pomContent.contains("spring-boot");
                }
            } catch (Exception ignored) {}
            view = new EnvironmentScanResultView(
                    "Java 17+",
                    isSpring ? "Spring Boot 3.x" : "Java Maven Project",
                    "Maven",
                    "JUnit 5",
                    "mvn clean package -DskipTests",
                    "mvn test",
                    detectedFiles
            );
        } else if (gradleFile.exists() || detectedFiles.stream().anyMatch(f -> f.endsWith("/build.gradle"))) {
            view = new EnvironmentScanResultView(
                    "Java/Kotlin",
                    "Gradle Project",
                    "Gradle",
                    "JUnit",
                    "./gradlew build -x test",
                    "./gradlew test",
                    detectedFiles
            );
        } else if (pkgFile.exists() || detectedFiles.stream().anyMatch(f -> f.endsWith("/package.json"))) {
            boolean isNext = false;
            try {
                if (pkgFile.exists()) {
                    String pkgContent = Files.readString(pkgFile.toPath(), StandardCharsets.UTF_8);
                    isNext = pkgContent.contains("next");
                }
            } catch (Exception ignored) {}
            view = new EnvironmentScanResultView(
                    "TypeScript / JavaScript",
                    isNext ? "Next.js 14+" : "Node.js Application",
                    "npm",
                    "Jest / Vitest",
                    "npm run build",
                    "npm test",
                    detectedFiles
            );
        } else if (reqFile.exists()) {
            view = new EnvironmentScanResultView(
                    "Python 3.x",
                    "Python Application",
                    "pip",
                    "pytest",
                    "pip install -r requirements.txt",
                    "pytest",
                    detectedFiles
            );
        } else if (goFile.exists()) {
            view = new EnvironmentScanResultView(
                    "Go",
                    "Go Module",
                    "go build",
                    "go test",
                    "go build ./...",
                    "go test ./...",
                    detectedFiles
            );
        } else {
            view = new EnvironmentScanResultView(
                    "General Software",
                    "Standard Repository",
                    "Custom",
                    "Custom",
                    "",
                    "",
                    detectedFiles
            );
        }

        view.setSecurityNotice(securityNotice);
        return view;
    }


    // ==== Phase 10 R9 深模块拆分：快照/Diff/回滚与基线操作委托到 jgit/ 包四缝，公开 API 不变 ====

    public void initWorkspace(File workspaceDir) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
    }

    public String createBaseline(File workspaceDir, String runId, String authorEmail) throws Exception {
        return snapshotOperations.createBaseline(workspaceDir, runId, authorEmail);
    }

    public StepSnapshotResult createStepSnapshot(File workspaceDir, String runId, String stepRunId,
                                                 String message, String authorEmail) throws Exception {
        return snapshotOperations.createStepSnapshot(workspaceDir, runId, stepRunId, message, authorEmail);
    }

    public void commitSnapshot(File workspaceDir, String message) throws Exception {
        snapshotOperations.commitSnapshot(workspaceDir, message);
    }

    public com.agenthub.domain.workspace.model.StructuredDiff computeStructuredDiff(File workspaceDir) throws Exception {
        return diffOperations.computeStructuredDiff(workspaceDir);
    }

    public com.agenthub.domain.workspace.model.StructuredDiff computeStructuredDiffBetweenCommits(File workspaceDir, String fromCommitHash, String toCommitHash) throws Exception {
        return diffOperations.computeStructuredDiffBetweenCommits(workspaceDir, fromCommitHash, toCommitHash);
    }

    public java.util.List<com.agenthub.domain.workspace.model.FileDiffEntry> computeWorkspaceDiff(File workspaceDir) throws Exception {
        return diffOperations.computeWorkspaceDiff(workspaceDir);
    }

    public void revertStepFiles(File workspaceDir, String baselineCommitHash, List<String> filePaths, String authorEmail) throws Exception {
        revertOperations.revertStepFiles(workspaceDir, baselineCommitHash, filePaths, authorEmail);
    }

    public void resetWorkspaceToBaseline(File workspaceDir, String baselineCommitHash) throws Exception {
        revertOperations.resetWorkspaceToBaseline(workspaceDir, baselineCommitHash);
    }

    public void revertSingleFile(File workspaceDir, String baselineCommitHash, String filePath, String authorEmail) throws Exception {
        revertOperations.revertSingleFile(workspaceDir, baselineCommitHash, filePath, authorEmail);
    }

    public List<String> getChangedFilesInCommit(File workspaceDir, String commitHash) throws Exception {
        return revertOperations.getChangedFilesInCommit(workspaceDir, commitHash);
    }

    public void resyncWorkspace(File workspaceDir, String baselineCommit) throws Exception {
        revertOperations.resyncWorkspace(workspaceDir, baselineCommit);
    }

    public String changeBaseline(File workspaceDir, String targetCommitHash) throws Exception {
        return revertOperations.changeBaseline(workspaceDir, targetCommitHash);
    }

    public void discardWorkspaceChanges(File workspaceDir, String baselineCommit) throws Exception {
        revertOperations.discardWorkspaceChanges(workspaceDir, baselineCommit);
    }

    public String restoreSnapshot(File workspaceDir, String snapshotCommitOrTag) throws Exception {
        return revertOperations.restoreSnapshot(workspaceDir, snapshotCommitOrTag);
    }

    public static String calculateFileChecksum(Path path) throws Exception {
        return JGitSupport.calculateFileChecksum(path);
    }

    public static String calculateBytesChecksum(byte[] bytes) {
        return JGitSupport.calculateBytesChecksum(bytes);
    }

    public static boolean isBinaryFile(File file) {
        return JGitSupport.isBinaryFile(file);
    }
}
