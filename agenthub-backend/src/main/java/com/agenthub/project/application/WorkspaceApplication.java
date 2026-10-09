package com.agenthub.project.application;

import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;

import java.util.List;

public interface WorkspaceApplication {

    StructuredDiff computeStructuredDiff(String workspaceIdOrPath) throws Exception;

    @Deprecated
    List<FileDiffEntry> computeDiff(String path) throws Exception;

    List<WorkspaceFileNode> listFiles(String workspaceIdOrPath);

    List<WorkspaceFileNode> listFiles(String workspaceIdOrPath, String relativeDirectoryPath);

    @Deprecated
    String getFileContent(String filePath) throws Exception;

    String getFileContent(String workspaceIdOrPath, String relativePath) throws Exception;

    WorkspaceFileDetailView getFileDetail(String workspaceIdOrPath, String relativePath) throws Exception;

    @Deprecated
    void saveFile(String filePath, String content) throws Exception;

    void saveFile(String workspaceIdOrPath, String relativePath, String content) throws Exception;

    void renameFile(String workspaceIdOrPath, String oldRelativePath, String newRelativePath) throws Exception;

    void deleteFile(String workspaceIdOrPath, String relativePath) throws Exception;

    byte[] downloadFile(String workspaceIdOrPath, String relativePath) throws Exception;

    byte[] archiveWorkspace(String workspaceIdOrPath) throws Exception;

    void checkWorkspaceAccess(String workspaceIdOrPath);

    WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String taskPrompt);
}
