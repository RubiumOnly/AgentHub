package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workflow.model.WorkflowModels.*;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workspace")
public class WorkflowAndDiffController {

    private final JGitWorkspaceManager gitManager;
    private final WorkflowEngineService workflowEngineService;

    public WorkflowAndDiffController(JGitWorkspaceManager gitManager, WorkflowEngineService workflowEngineService) {
        this.gitManager = gitManager;
        this.workflowEngineService = workflowEngineService;
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
        File dir = new File(path);
        List<FileDiffEntry> diff = gitManager.computeWorkspaceDiff(dir);
        return Result.ok(diff);
    }

    @PostMapping("/workflow/execute")
    public Result<WorkflowExecutionResult> executeWorkflow(@RequestBody ExecuteWorkflowRequest req) {
        WorkflowExecutionResult result = workflowEngineService.executeWorkflow(
                req.workflow,
                req.workspacePath != null ? req.workspacePath : "d:/work/agenthub/data/workspaces/default",
                req.taskPrompt
        );
        return Result.ok(result);
    }

    @GetMapping("/files")
    public Result<List<WorkspaceFileNode>> listFiles(@RequestParam("path") String path) {
        File root = new File(path);
        List<WorkspaceFileNode> fileNodes = new ArrayList<>();
        if (!root.exists()) {
            return Result.ok(fileNodes);
        }

        scanFiles(root, root, fileNodes);
        return Result.ok(fileNodes);
    }

    @GetMapping("/file/content")
    public Result<String> getFileContent(@RequestParam("filePath") String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists() || file.isDirectory()) {
            return Result.fail(404, "File not found: " + filePath);
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        return Result.ok(content);
    }

    @PostMapping("/file/save")
    public Result<Void> saveFile(@RequestBody SaveFileRequest req) throws Exception {
        File file = new File(req.filePath);
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        Files.writeString(file.toPath(), req.content != null ? req.content : "", StandardCharsets.UTF_8);
        return Result.ok();
    }

    private void scanFiles(File current, File root, List<WorkspaceFileNode> nodes) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().equals(".git")) continue;
            String relative = root.toPath().relativize(f.toPath()).toString().replace("\\", "/");
            nodes.add(new WorkspaceFileNode(f.getName(), relative, f.isDirectory(), f.length()));
            if (f.isDirectory()) {
                scanFiles(f, root, nodes);
            }
        }
    }
}
