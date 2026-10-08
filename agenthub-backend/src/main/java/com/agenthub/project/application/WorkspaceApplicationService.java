package com.agenthub.project.application;

import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class WorkspaceApplicationService implements WorkspaceApplication {

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    private final JGitWorkspaceManager gitManager;
    private final WorkflowEngineService workflowEngineService;
    private final WorkspaceResolver workspaceResolver;

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                     WorkflowEngineService workflowEngineService,
                                     WorkspaceResolver workspaceResolver) {
        this.gitManager = gitManager;
        this.workflowEngineService = workflowEngineService;
        this.workspaceResolver = workspaceResolver;
    }

    @Override
    public List<FileDiffEntry> computeDiff(String path) throws Exception {
        Path safeDir = workspaceResolver.resolveLegacyPath(path, false);
        return gitManager.computeWorkspaceDiff(safeDir.toFile());
    }

    @Override
    public List<WorkspaceFileNode> listFiles(String path) {
        Path safeRoot = workspaceResolver.resolveLegacyPath(path, false);
        File root = safeRoot.toFile();
        List<WorkspaceFileNode> fileNodes = new ArrayList<>();
        if (!root.exists()) {
            return fileNodes;
        }
        scanFiles(root, root, fileNodes);
        return fileNodes;
    }

    @Override
    public String getFileContent(String filePath) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, false);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + filePath);
        }
        return Files.readString(safeFile, StandardCharsets.UTF_8);
    }

    @Override
    public void saveFile(String filePath, String content) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, true);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String taskPrompt) {
        String targetPath = (workspacePath != null && !workspacePath.isBlank())
                ? workspacePath : workspaceBaseDir + "/default";
        Path safeWorkspace = workspaceResolver.resolveLegacyPath(targetPath, false);
        return workflowEngineService.executeWorkflow(workflow, safeWorkspace.toString(), taskPrompt);
    }

    private void scanFiles(File current, File root, List<WorkspaceFileNode> nodes) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().equalsIgnoreCase(".git")) continue;
            String relative = root.toPath().relativize(f.toPath()).toString().replace("\\", "/");
            nodes.add(new WorkspaceFileNode(f.getName(), relative, f.isDirectory(), f.length()));
            if (f.isDirectory()) {
                scanFiles(f, root, nodes);
            }
        }
    }
}
