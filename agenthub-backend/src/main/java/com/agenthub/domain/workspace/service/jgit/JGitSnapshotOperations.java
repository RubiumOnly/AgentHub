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
 * 快照缝（Phase 10 R9 自 JGitWorkspaceManager 抽出）：工作区初始化、Run 基线、
 * 步骤快照与提交；标签语义 snapshot-{stepRunId} 集中于此。
 */
public class JGitSnapshotOperations {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JGitSnapshotOperations.class);


    public void initWorkspace(File workspaceDir) throws Exception {
        if (!workspaceDir.exists()) {
            workspaceDir.mkdirs();
        }
        File gitDir = new File(workspaceDir, ".git");
        File headFile = new File(gitDir, "HEAD");
        if (!gitDir.exists() || !headFile.exists()) {
            try (Git git = Git.init().setDirectory(workspaceDir).call()) {
                try {
                    File readme = new File(workspaceDir, "README.md");
                    if (!readme.exists()) {
                        Files.writeString(readme.toPath(), "# AgentHub Project Workspace\nInitial Baseline.", StandardCharsets.UTF_8);
                    }
                    git.add().addFilepattern(".").call();
                    git.commit().setMessage("Initial project baseline commit").setAuthor(JGitSupport.DEFAULT_AUTHOR, JGitSupport.DEFAULT_EMAIL).call();
                    log.info("Initialized Git workspace baseline at: {}", workspaceDir.getAbsolutePath());
                } finally {
                    JGitSupport.closeRepositoryQuietly(git, workspaceDir);
                }
            }
        }
    }

    public String createBaseline(File workspaceDir, String runId, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : JGitSupport.DEFAULT_EMAIL;
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();

                Status status = git.status().call();
                RevCommit commit;
                if (!status.isClean()) {
                    commit = git.commit()
                            .setMessage("Baseline commit for Run " + runId)
                            .setAuthor(JGitSupport.DEFAULT_AUTHOR, author)
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public com.agenthub.domain.workspace.service.JGitWorkspaceManager.StepSnapshotResult createStepSnapshot(File workspaceDir, String runId, String stepRunId,
                                                String message, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : JGitSupport.DEFAULT_EMAIL;

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
                            .setAuthor(JGitSupport.DEFAULT_AUTHOR, author)
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
                        checksums.put(fileRel, JGitSupport.calculateFileChecksum(f.toPath()));
                    }
                }

                return new com.agenthub.domain.workspace.service.JGitWorkspaceManager.StepSnapshotResult(
                        commit.getName(),
                        tagName,
                        changedFiles,
                        checksums,
                        author,
                        System.currentTimeMillis()
                );
            } finally {
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }

    public void commitSnapshot(File workspaceDir, String message) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            try {
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.commit().setMessage(message).setAuthor(JGitSupport.DEFAULT_AUTHOR, JGitSupport.DEFAULT_EMAIL).call();
            } finally {
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
            }
        }
    }
}
