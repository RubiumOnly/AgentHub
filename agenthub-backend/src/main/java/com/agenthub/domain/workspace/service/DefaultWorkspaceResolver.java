package com.agenthub.domain.workspace.service;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

@Service
public class DefaultWorkspaceResolver implements WorkspaceResolver {

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String baseDirString;

    public DefaultWorkspaceResolver() {}

    public DefaultWorkspaceResolver(String baseDirString) {
        this.baseDirString = baseDirString;
    }

    private Path getBaseDir() {
        String dirStr = (baseDirString != null && !baseDirString.isBlank()) ? baseDirString : "./data/workspaces";
        Path base = Path.of(dirStr).toAbsolutePath().normalize();
        try {
            if (!Files.exists(base)) {
                Files.createDirectories(base);
            }
        } catch (IOException ignored) {}
        return base;
    }

    @Override
    public Path resolvePathForRead(String workspaceIdOrPath, String relativePath) {
        Path root = getWorkspaceRoot(workspaceIdOrPath);
        validateRelativePathString(relativePath);

        Path candidate = root.resolve(relativePath).normalize();

        // Stage 3: Root containment check
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // Stage 5: Case-insensitive .git protection
        checkGitProtection(root, candidate);

        // Stage 4: Real path containment check
        validateRealPathContainment(root, candidate);

        return candidate;
    }

    @Override
    public Path resolvePathForWrite(String workspaceIdOrPath, String relativePath) {
        Path root = getWorkspaceRoot(workspaceIdOrPath);
        validateRelativePathString(relativePath);

        Path candidate = root.resolve(relativePath).normalize();

        // Stage 3: Root containment check
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // Stage 5: Case-insensitive .git protection
        checkGitProtection(root, candidate);

        // Stage 4: Real path containment check (deepest existing parent)
        validateRealPathContainment(root, candidate);

        return candidate;
    }

    @Override
    public Path getWorkspaceRoot(String workspaceIdOrPath) {
        if (workspaceIdOrPath == null || workspaceIdOrPath.isBlank()) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Workspace ID or path cannot be empty");
        }
        if (workspaceIdOrPath.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Null byte detected in workspace path");
        }

        Path root = resolveLegacyPath(workspaceIdOrPath, false);
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Failed to create workspace directory: " + e.getMessage());
        }
        return root;
    }

    @Override
    public Path resolveLegacyPath(String clientPath, boolean isWrite) {
        if (clientPath == null || clientPath.isBlank()) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Client path cannot be empty");
        }
        if (clientPath.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Null byte detected in path");
        }
        if (clientPath.contains("::$DATA")) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Illegal character sequence in path");
        }

        Path base = getBaseDir();
        Path candidate;
        try {
            Path p = Path.of(clientPath);
            if (p.isAbsolute()) {
                candidate = p.normalize();
            } else {
                Path cwdCandidate = p.toAbsolutePath().normalize();
                if (cwdCandidate.startsWith(base)) {
                    candidate = cwdCandidate;
                } else {
                    candidate = base.resolve(p).normalize();
                }
            }
        } catch (InvalidPathException e) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Invalid workspace path format: " + e.getMessage());
        }

        // Stage 3: Root containment against base directory
        if (!candidate.startsWith(base)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // Stage 5: .git directory protection
        checkGitProtection(base, candidate);

        // Stage 4: Real path containment against base directory
        validateRealPathContainment(base, candidate);

        return candidate;
    }

    private void validateRelativePathString(String relPath) {
        if (relPath == null || relPath.isBlank()) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Relative path cannot be empty");
        }
        if (relPath.indexOf('\0') >= 0) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Null byte detected in path");
        }
        if (relPath.contains("::$DATA")) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Illegal character sequence in relative path");
        }
        if (relPath.startsWith("/") || relPath.startsWith("\\") || relPath.contains(":") || Path.of(relPath).isAbsolute()) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Absolute path or colon not allowed for relativePath: " + relPath);
        }
    }

    private void checkGitProtection(Path root, Path candidate) {
        Path relative;
        try {
            relative = root.relativize(candidate);
        } catch (IllegalArgumentException e) {
            relative = candidate;
        }

        for (Path segment : relative) {
            String seg = segment.toString().toLowerCase(Locale.ROOT).strip().replaceAll("\\.+$", "");
            if (seg.equals(".git")) {
                throw new BusinessException(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED,
                        "Direct read or modification of .git directory is forbidden");
            }
        }
    }

    private void validateRealPathContainment(Path root, Path candidate) {
        Path realRoot;
        try {
            if (!Files.exists(root)) {
                Files.createDirectories(root);
            }
            realRoot = root.toRealPath();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Unable to resolve workspace real path: " + e.getMessage());
        }

        Path p = candidate;
        while (p != null && !Files.exists(p)) {
            p = p.getParent();
        }

        if (p != null) {
            try {
                Path realP = p.toRealPath();
                if (!realP.startsWith(realRoot)) {
                    throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                            "Directory traversal outside workspace is strictly forbidden");
                }
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Unable to resolve real path: " + e.getMessage());
            }
        }
    }
}
