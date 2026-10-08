package com.agenthub.domain.workspace.service;

import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import org.eclipse.jgit.api.Git;
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
                File readme = new File(workspaceDir, "README.md");
                if (!readme.exists()) {
                    Files.writeString(readme.toPath(), "# AgentHub Project Workspace\nInitial Baseline.", StandardCharsets.UTF_8);
                }
                git.add().addFilepattern(".").call();
                git.commit().setMessage("Initial project baseline commit").setAuthor(DEFAULT_AUTHOR, DEFAULT_EMAIL).call();
                log.info("Initialized Git workspace baseline at: {}", workspaceDir.getAbsolutePath());
            }
        }
    }

    public String createBaseline(File workspaceDir, String runId, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
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
        }
    }

    public StepSnapshotResult createStepSnapshot(File workspaceDir, String runId, String stepRunId,
                                                String message, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
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
        }
    }

    public void commitSnapshot(File workspaceDir, String message) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir)) {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            git.commit().setMessage(message).setAuthor(DEFAULT_AUTHOR, DEFAULT_EMAIL).call();
        }
    }

    public StructuredDiff computeStructuredDiff(File workspaceDir) throws Exception {
        initWorkspace(workspaceDir);
        List<FileDiffEntry> diffEntries = new ArrayList<>();
        int totalAdded = 0;
        int totalDeleted = 0;
        String baselineHash = "";
        String headHash = "";

        try (Git git = Git.open(workspaceDir);
             Repository repo = git.getRepository();
             ObjectReader reader = repo.newObjectReader()) {

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

    public List<FileDiffEntry> computeWorkspaceDiff(File workspaceDir) throws Exception {
        return computeStructuredDiff(workspaceDir).getEntries();
    }

    public void revertStepFiles(File workspaceDir, String baselineCommitHash, List<String> filePaths, String authorEmail) throws Exception {
        initWorkspace(workspaceDir);
        try (Git git = Git.open(workspaceDir);
             Repository repo = git.getRepository()) {

            String author = (authorEmail != null && !authorEmail.isBlank()) ? authorEmail : DEFAULT_EMAIL;

            for (String relPath : filePaths) {
                if (relPath == null || relPath.isBlank() || relPath.contains(".git")) continue;
                String normalizedPath = relPath.replace('\\', '/').replaceAll("^/+", "");

                byte[] baselineContent = readFileAtCommit(repo, baselineCommitHash, normalizedPath);
                File fileOnDisk = new File(workspaceDir, normalizedPath);

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

    public static String calculateFileChecksum(Path path) {
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return "";
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
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
}
