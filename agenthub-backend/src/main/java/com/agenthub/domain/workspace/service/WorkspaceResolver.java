package com.agenthub.domain.workspace.service;

import java.nio.file.Path;

public interface WorkspaceResolver {

    /**
     * Resolves and validates an existing file/directory within the workspace for read operations.
     * Throws BusinessException if path is outside workspace, invalid, or accesses .git metadata.
     */
    Path resolvePathForRead(String workspaceIdOrPath, String relativePath);

    /**
     * Resolves and validates a file path within the workspace for write/create operations.
     * Validates parent containment and blocks .git tampering.
     */
    Path resolvePathForWrite(String workspaceIdOrPath, String relativePath);

    /**
     * Returns the canonical, normalized root path of the specified workspace.
     */
    Path getWorkspaceRoot(String workspaceIdOrPath);

    /**
     * Resolves a client-supplied legacy path or relative path safely within the configured base directory.
     */
    Path resolveLegacyPath(String clientPath, boolean isWrite);
}
