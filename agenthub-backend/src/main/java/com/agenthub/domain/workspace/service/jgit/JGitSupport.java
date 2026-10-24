package com.agenthub.domain.workspace.service.jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * JGit 共享工具缝（Phase 10 R9 自 JGitWorkspaceManager 逐字抽出）：校验和、二进制判定、
 * 目录复制、仓库清理、冲突检测与提交内容读取的唯一实现。
 */
public final class JGitSupport {

    public static final String DEFAULT_AUTHOR = "AgentHub";
    public static final String DEFAULT_EMAIL = "bot@agenthub.local";

    private static final Logger log = LoggerFactory.getLogger(JGitSupport.class);

    private JGitSupport() {
    }

    public static void copyDirectoryContents(File sourceDir, File targetDir) {
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

    public static boolean checkFileConflict(File file) {
        if (!file.exists() || file.isDirectory()) return false;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return content.contains("<<<<<<< ") && content.contains("=======");
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void closeRepositoryQuietly(Git git, File workspaceDir) {
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

    public static byte[] readFileAtCommit(Repository repo, String commitHash, String relativePath) throws IOException {
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
}
