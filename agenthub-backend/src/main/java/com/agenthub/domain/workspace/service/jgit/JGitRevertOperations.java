package com.agenthub.domain.workspace.service.jgit;

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
/**
 * 回滚与基线缝（Phase 10 R9 自 JGitWorkspaceManager 抽出）：步骤文件回滚、
 * 整树重置、单文件回滚与 resync/改基线/丢弃/恢复。
 */
public class JGitRevertOperations {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JGitRevertOperations.class);

    private final JGitSnapshotOperations snapshotOperations;

    public JGitRevertOperations(JGitSnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }


    public void revertStepFiles(File workspaceDir, String baselineCommitHash, List<String> filePaths, String authorEmail) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
        if (filePaths == null || filePaths.isEmpty()) {
            resetWorkspaceToBaseline(workspaceDir, baselineCommitHash);
            return;
        }

        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId baselineId = repo.resolve(baselineCommitHash);
                if (baselineId == null || !repo.hasObject(baselineId)) {
                    throw new IllegalArgumentException("Baseline commit hash not found in repository: " + baselineCommitHash);
                }

                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : JGitSupport.DEFAULT_EMAIL;
                Path wsPath = workspaceDir.toPath().toAbsolutePath().normalize();

                boolean hasStagedChanges = false;
                for (String relPath : filePaths) {
                    if (relPath == null || relPath.isBlank() || relPath.contains(".git")) continue;
                    String normalizedPath = relPath.replace('\\', '/').replaceAll("^/+", "");
                    Path targetPath = wsPath.resolve(normalizedPath).normalize();
                    if (!targetPath.startsWith(wsPath)) {
                        log.warn("Path traversal detected in revertStepFiles, skipping: {}", relPath);
                        continue;
                    }

                    byte[] baselineContent = JGitSupport.readFileAtCommit(repo, baselineCommitHash, normalizedPath);
                    File fileOnDisk = targetPath.toFile();

                    if (baselineContent != null) {
                        if (fileOnDisk.getParentFile() != null && !fileOnDisk.getParentFile().exists()) {
                            fileOnDisk.getParentFile().mkdirs();
                        }
                        Files.write(fileOnDisk.toPath(), baselineContent);
                        git.add().addFilepattern(normalizedPath).call();
                        hasStagedChanges = true;
                        log.info("Restored file [{}] to baseline commit [{}]", normalizedPath, baselineCommitHash);
                    } else {
                        if (fileOnDisk.exists()) {
                            fileOnDisk.delete();
                            log.info("Deleted newly added file [{}] on revert", normalizedPath);
                        }
                        git.rm().addFilepattern(normalizedPath).call();
                        hasStagedChanges = true;
                    }
                }

                if (hasStagedChanges) {
                    Status status = git.status().call();
                    if (!status.getAdded().isEmpty() || !status.getChanged().isEmpty() || !status.getRemoved().isEmpty()) {
                        git.commit()
                                .setMessage("[Revert] Rollback step files to baseline " + baselineCommitHash)
                                .setAuthor(JGitSupport.DEFAULT_AUTHOR, author)
                                .call();
                        log.info("Committed revert to baseline {} in workspace {}", baselineCommitHash, workspaceDir.getAbsolutePath());
                    }
                }
            } finally {
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void resetWorkspaceToBaseline(File workspaceDir, String baselineCommitHash) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                Repository repo = git.getRepository();
                ObjectId baselineId = repo.resolve(baselineCommitHash);
                if (baselineId == null || !repo.hasObject(baselineId)) {
                    throw new IllegalArgumentException("Baseline commit hash not found in repository: " + baselineCommitHash);
                }
                git.reset().setMode(ResetType.HARD).setRef(baselineCommitHash).call();
                git.clean().setCleanDirectories(true).call();
                log.info("Reset workspace [{}] hard to baseline commit [{}]", workspaceDir.getAbsolutePath(), baselineCommitHash);
            } finally {
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void revertSingleFile(File workspaceDir, String baselineCommitHash, String filePath, String authorEmail) throws Exception {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File path to revert must not be empty");
        }
        revertStepFiles(workspaceDir, baselineCommitHash, List.of(filePath), authorEmail);
    }

    public List<String> getChangedFilesInCommit(File workspaceDir, String commitHash) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }



    public void resyncWorkspace(File workspaceDir, String baselineCommit) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public String changeBaseline(File workspaceDir, String targetCommitHash) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void discardWorkspaceChanges(File workspaceDir, String baselineCommit) throws Exception {
        resyncWorkspace(workspaceDir, baselineCommit);
    }

    public String restoreSnapshot(File workspaceDir, String snapshotCommitOrTag) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }
}
