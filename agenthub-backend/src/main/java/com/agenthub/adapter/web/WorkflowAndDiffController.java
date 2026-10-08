package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workflow.model.WorkflowModels.*;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
public class WorkflowAndDiffController {

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    private final JGitWorkspaceManager gitManager;
    private final WorkflowEngineService workflowEngineService;
    private final WorkspaceResolver workspaceResolver;

    public WorkflowAndDiffController(JGitWorkspaceManager gitManager,
                                    WorkflowEngineService workflowEngineService,
                                    WorkspaceResolver workspaceResolver) {
        this.gitManager = gitManager;
        this.workflowEngineService = workflowEngineService;
        this.workspaceResolver = workspaceResolver;
    }

    public static class ExecuteWorkflowRequest {
        public WorkflowDefinition workflow;
        public String workspacePath;
        public String taskPrompt;
    }

    public static class SaveFileRequest {
        public String filePath;
        public String content;
    }

    public static class WorkspaceFileNode {
        public String name;
        public String relativePath;
        public boolean isDirectory;
        public long size;

        public WorkspaceFileNode() {}
        public WorkspaceFileNode(String name, String relativePath, boolean isDirectory, long size) {
            this.name = name;
            this.relativePath = relativePath;
            this.isDirectory = isDirectory;
            this.size = size;
        }
    }

    @GetMapping("/diff")
    public Result<List<FileDiffEntry>> getDiff(@RequestParam("path") String path) throws Exception {
        Path safeDir = workspaceResolver.resolveLegacyPath(path, false);
        List<FileDiffEntry> diff = gitManager.computeWorkspaceDiff(safeDir.toFile());
        return Result.ok(diff);
    }

    @PostMapping("/workflow/execute")
    public Result<WorkflowExecutionResult> executeWorkflow(@RequestBody ExecuteWorkflowRequest req) {
        String targetPath = (req.workspacePath != null && !req.workspacePath.isBlank())
                ? req.workspacePath : workspaceBaseDir + "/default";
        Path safeWorkspace = workspaceResolver.resolveLegacyPath(targetPath, false);
        WorkflowExecutionResult result = workflowEngineService.executeWorkflow(
                req.workflow,
                safeWorkspace.toString(),
                req.taskPrompt
        );
        return Result.ok(result);
    }

    @GetMapping("/files")
    public Result<List<WorkspaceFileNode>> listFiles(@RequestParam("path") String path) {
        Path safeRoot = workspaceResolver.resolveLegacyPath(path, false);
        File root = safeRoot.toFile();
        List<WorkspaceFileNode> fileNodes = new ArrayList<>();
        if (!root.exists()) {
            return Result.ok(fileNodes);
        }

        scanFiles(root, root, fileNodes);
        return Result.ok(fileNodes);
    }

    @GetMapping("/file/content")
    public Result<String> getFileContent(@RequestParam("filePath") String filePath) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, false);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            return Result.fail(404, "File not found: " + filePath);
        }
        String content = Files.readString(safeFile, StandardCharsets.UTF_8);
        return Result.ok(content);
    }

    @PostMapping("/file/save")
    public Result<Void> saveFile(@RequestBody SaveFileRequest req) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(req.filePath, true);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, req.content != null ? req.content : "", StandardCharsets.UTF_8);
        return Result.ok();
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
