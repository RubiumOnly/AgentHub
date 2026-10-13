package com.agenthub.project.application;

import com.agenthub.project.dto.context.ContextSourceView;
import com.agenthub.project.dto.context.RegisterContextSourceCommand;

import java.util.List;

/**
 * Versioned Context Source Application Interface.
 */
public interface ContextSourceApplication {

    List<ContextSourceView> listContextSources(String projectId);

    ContextSourceView getContextSource(String projectId, String sourceId);

    ContextSourceView registerContextSource(String projectId, RegisterContextSourceCommand cmd);

    List<ContextSourceView> autoDiscoverContextSources(String projectId, String workspaceId, String commitHash);
}
