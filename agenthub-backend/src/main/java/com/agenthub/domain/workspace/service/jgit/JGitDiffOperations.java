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
 * 结构化 Diff 缝（Phase 10 R9 自 JGitWorkspaceManager 抽出）：工作区级与
 * 提交区间的 Tree-to-Tree 结构化 Diff；行数统计与冲突检测语义集中。
 */
public class JGitDiffOperations {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(JGitDiffOperations.class);

    private final JGitSnapshotOperations snapshotOperations;

    public JGitDiffOperations(JGitSnapshotOperations snapshotOperations) {
        this.snapshotOperations = snapshotOperations;
    }


    public StructuredDiff computeStructuredDiff(File workspaceDir) throws Exception {
        snapshotOperations.initWorkspace(workspaceDir);
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

                    boolean isBinary = JGitSupport.isBinaryFile(targetFile) || diffText.contains("Binary files differ");
                    boolean hasConflict = JGitSupport.checkFileConflict(targetFile);
                    String checksum = (targetFile.exists() && targetFile.isFile()) ? JGitSupport.calculateFileChecksum(targetFile.toPath()) : "";

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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
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
        snapshotOperations.initWorkspace(workspaceDir);
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
                                ? JGitSupport.readFileAtCommit(repo, toCommitHash, targetRelPath)
                                : null;
                        boolean isBinary = diffText.contains("Binary files differ");
                        boolean hasConflict = false;
                        String checksum = "";
                        if (committedBytes != null) {
                            checksum = JGitSupport.calculateBytesChecksum(committedBytes);
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
                JGitSupport.closeRepositoryQuietly(git, workspaceDir);
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
}
