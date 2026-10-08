package com.agenthub.domain.workspace.service;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class DefaultWorkspaceResolver implements WorkspaceResolver {

    private static final Pattern RESERVED_DEVICE_PATTERN = Pattern.compile(
            "^(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?$"
    );

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String baseDirString;

    @Autowired(required = false)
    private WorkspaceRepository workspaceRepository;

    public DefaultWorkspaceResolver() {}

    public DefaultWorkspaceResolver(String baseDirString) {
        this.baseDirString = baseDirString;
    }

    public DefaultWorkspaceResolver(String baseDirString, WorkspaceRepository workspaceRepository) {
        this.baseDirString = baseDirString;
        this.workspaceRepository = workspaceRepository;
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

        // 1. Root containment check
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // 2. Case-insensitive .git protection
        checkGitProtection(root, candidate);

        // 3. Real path containment & symlink check
        validateRealPathContainment(root, candidate);

        return candidate;
    }

    @Override
    public Path resolvePathForWrite(String workspaceIdOrPath, String relativePath) {
        Path root = getWorkspaceRoot(workspaceIdOrPath);
        validateRelativePathString(relativePath);

        Path candidate = root.resolve(relativePath).normalize();

        // 1. Root containment check
        if (!candidate.startsWith(root)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // 2. Case-insensitive .git protection
        checkGitProtection(root, candidate);

        // 3. Real path containment check (deepest existing parent)
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
        if (workspaceIdOrPath.contains("::$DATA")) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Illegal character sequence in workspace path");
        }

        Path base = getBaseDir();

        // If repository is available, check if workspaceIdOrPath is a recorded workspace ID or project ID
        if (workspaceRepository != null) {
            Optional<WorkspaceEntity> wsOpt = workspaceRepository.findById(workspaceIdOrPath);
            if (wsOpt.isEmpty()) {
                wsOpt = workspaceRepository.findByProjectId(workspaceIdOrPath);
            }
            if (wsOpt.isPresent()) {
                String relativeRoot = wsOpt.get().getRelativeRoot();
                Path wsRoot = base.resolve(relativeRoot).normalize();
                if (!wsRoot.startsWith(base)) {
                    throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                            "Configured workspace relative root escapes base directory");
                }
                checkGitProtection(base, wsRoot);
                ensureDirectoryExists(wsRoot);
                validateRealPathContainment(base, wsRoot);
                return wsRoot;
            }
        }

        // Fallback: Resolve as legacy path or direct directory name under baseDir
        Path root = resolveLegacyPath(workspaceIdOrPath, false);
        ensureDirectoryExists(root);
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

        // Check reserved device names in client path
        checkDeviceNames(clientPath);

        // Root containment against base directory
        if (!candidate.startsWith(base)) {
            throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                    "Directory traversal outside workspace is strictly forbidden");
        }

        // .git directory protection
        checkGitProtection(base, candidate);

        // Real path containment against base directory
        validateRealPathContainment(base, candidate);

        return candidate;
    }

    private void ensureDirectoryExists(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID,
                    "Failed to create workspace directory: " + e.getMessage());
        }
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
        checkDeviceNames(relPath);
    }

    private void checkDeviceNames(String pathStr) {
        String normalized = pathStr.replace('\\', '/');
        String[] parts = normalized.split("/");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            if (RESERVED_DEVICE_PATTERN.matcher(trimmed).matches()) {
                throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID,
                        "Reserved system device name not allowed: " + trimmed);
            }
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

        // 1. Direct check on candidate if it exists
        if (Files.exists(candidate)) {
            try {
                Path realCandidate = candidate.toRealPath();
                if (!realCandidate.startsWith(realRoot)) {
                    throw new BusinessException(ErrorCode.WORKSPACE_TRAVERSAL_DENIED,
                            "Symbolic link or directory traversal escapes workspace root: " + candidate);
                }
            } catch (IOException e) {
                throw new BusinessException(ErrorCode.WORKSPACE_PATH_INVALID, "Unable to resolve candidate real path: " + e.getMessage());
            }
        }

        // 2. Check all existing parent directories
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
