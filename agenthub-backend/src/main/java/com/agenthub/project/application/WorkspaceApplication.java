package com.agenthub.project.application;

import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.project.dto.WorkspaceFileNode;

import java.util.List;

public interface WorkspaceApplication {
    List<FileDiffEntry> computeDiff(String path) throws Exception;
    List<WorkspaceFileNode> listFiles(String path);
    String getFileContent(String filePath) throws Exception;
    void saveFile(String filePath, String content) throws Exception;
    WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String taskPrompt);
}
