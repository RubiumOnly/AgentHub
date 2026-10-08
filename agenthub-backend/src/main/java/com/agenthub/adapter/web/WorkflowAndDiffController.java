package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileNode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workspace")
public class WorkflowAndDiffController {

    private final WorkspaceApplication workspaceApplication;

    public WorkflowAndDiffController(WorkspaceApplication workspaceApplication) {
        this.workspaceApplication = workspaceApplication;
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

    @GetMapping("/diff")
    public Result<List<FileDiffEntry>> getDiff(@RequestParam("path") String path) throws Exception {
        List<FileDiffEntry> diff = workspaceApplication.computeDiff(path);
        return Result.ok(diff);
    }

    @PostMapping("/workflow/execute")
    public Result<WorkflowExecutionResult> executeWorkflow(@RequestBody ExecuteWorkflowRequest req) {
        WorkflowExecutionResult result = workspaceApplication.executeWorkflow(
                req.workflow,
                req.workspacePath,
                req.taskPrompt
        );
        return Result.ok(result);
    }

    @GetMapping("/files")
    public Result<List<WorkspaceFileNode>> listFiles(@RequestParam("path") String path) {
        List<WorkspaceFileNode> fileNodes = workspaceApplication.listFiles(path);
        return Result.ok(fileNodes);
    }

    @GetMapping("/file/content")
    public Result<String> getFileContent(@RequestParam("filePath") String filePath) throws Exception {
        String content = workspaceApplication.getFileContent(filePath);
        return Result.ok(content);
    }

    @PostMapping("/file/save")
    public Result<Void> saveFile(@RequestBody SaveFileRequest req) throws Exception {
        workspaceApplication.saveFile(req.filePath, req.content);
        return Result.ok();
    }
}
