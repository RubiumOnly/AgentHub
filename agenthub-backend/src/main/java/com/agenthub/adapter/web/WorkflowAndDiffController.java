package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.identity.infrastructure.security.TokenProvider;
import com.agenthub.project.application.WorkspaceApplication;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @deprecated Legacy workspace endpoint accepting raw paths.
 * In production profile, this endpoint is strictly disabled to enforce workspaceId-based boundaries.
 * Use /api/workspaces/{workspaceId}/* endpoints instead.
 */
@Deprecated
@RestController
@RequestMapping("/api/workspace")
public class WorkflowAndDiffController {

    private static final Logger log = LoggerFactory.getLogger(WorkflowAndDiffController.class);

    private final WorkspaceApplication workspaceApplication;
    private final TokenProvider tokenProvider;

    public WorkflowAndDiffController(WorkspaceApplication workspaceApplication,
                                    @Autowired(required = false) TokenProvider tokenProvider) {
        this.workspaceApplication = workspaceApplication;
        this.tokenProvider = tokenProvider;
    }

    private void enforceProductionPolicy() {
        if (tokenProvider != null && tokenProvider.isProd()) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    "Legacy /api/workspace endpoint is strictly disabled in production. Use /api/workspaces/{workspaceId}/*");
        }
        log.warn("Legacy /api/workspace endpoint accessed in non-production environment");
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
        enforceProductionPolicy();
        List<FileDiffEntry> diff = workspaceApplication.computeDiff(path);
        return Result.ok(diff);
    }

    @PostMapping("/workflow/execute")
    public Result<WorkflowExecutionResult> executeWorkflow(@RequestBody ExecuteWorkflowRequest req) {
        enforceProductionPolicy();
        WorkflowExecutionResult result = workspaceApplication.executeWorkflow(
                req.workflow,
                req.workspacePath,
                req.taskPrompt
        );
        return Result.ok(result);
    }

    @GetMapping("/files")
    public Result<List<WorkspaceFileNode>> listFiles(@RequestParam("path") String path) {
        enforceProductionPolicy();
        List<WorkspaceFileNode> fileNodes = workspaceApplication.listFiles(path);
        return Result.ok(fileNodes);
    }

    @GetMapping("/file/content")
    public Result<String> getFileContent(@RequestParam("filePath") String filePath) throws Exception {
        enforceProductionPolicy();
        String content = workspaceApplication.getFileContent(filePath);
        return Result.ok(content);
    }

    @PostMapping("/file/save")
    public Result<Void> saveFile(@RequestBody SaveFileRequest req) throws Exception {
        enforceProductionPolicy();
        workspaceApplication.saveFile(req.filePath, req.content);
        return Result.ok();
    }
}
