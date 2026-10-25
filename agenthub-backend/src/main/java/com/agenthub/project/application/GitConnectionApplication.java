package com.agenthub.project.application;

import com.agenthub.project.dto.connection.AuthorizeGitConnectionCommand;
import com.agenthub.project.dto.connection.GitConnectionView;

import java.util.List;

/**
 * Git provider connection lifecycle use cases (Phase 11):
 * authorize with a credential REFERENCE, verify repository connectivity,
 * and revoke idempotently with full audit.
 */
public interface GitConnectionApplication {

    GitConnectionView authorize(String projectId, AuthorizeGitConnectionCommand cmd);

    List<GitConnectionView> list(String projectId);

    GitConnectionView testConnection(String projectId, String connectionId);

    GitConnectionView revoke(String projectId, String connectionId);
}
