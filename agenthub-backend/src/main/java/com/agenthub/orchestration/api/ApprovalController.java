package com.agenthub.orchestration.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.orchestration.application.ApprovalApplication;
import com.agenthub.orchestration.dto.ApprovalDecisionCommand;
import com.agenthub.orchestration.dto.ApprovalView;
import com.agenthub.shared.context.RequestContext;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final ApprovalApplication approvalApplication;

    public ApprovalController(ApprovalApplication approvalApplication) {
        this.approvalApplication = approvalApplication;
    }

    public static class DirectDecisionRequest {
        public String reason;
    }

    @GetMapping("/{id}")
    public Result<ApprovalView> getApproval(@PathVariable("id") String id) {
        ApprovalView view = approvalApplication.getApprovalById(id);
        return Result.ok(view);
    }

    @GetMapping("/runs/{runId}")
    public Result<List<ApprovalView>> listApprovalsByRun(@PathVariable("runId") String runId) {
        List<ApprovalView> views = approvalApplication.listApprovalsByRunId(runId);
        return Result.ok(views);
    }

    @PostMapping("/{id}/decision")
    public Result<ApprovalView> decide(
            @PathVariable("id") String id,
            @RequestBody ApprovalDecisionCommand cmd) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to review approval");
        }
        ApprovalView view = approvalApplication.decide(id, cmd);
        return Result.ok(view);
    }

    @PostMapping("/{id}/approve")
    public Result<ApprovalView> approve(
            @PathVariable("id") String id,
            @RequestBody(required = false) DirectDecisionRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to approve");
        }
        String reason = req != null && req.reason != null ? req.reason : "Approved by user";
        ApprovalView view = approvalApplication.approve(id, reason);
        return Result.ok(view);
    }

    @PostMapping("/{id}/reject")
    public Result<ApprovalView> reject(
            @PathVariable("id") String id,
            @RequestBody(required = false) DirectDecisionRequest req) {
        String currentUserId = RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "Authentication required to reject");
        }
        String reason = req != null && req.reason != null ? req.reason : "Rejected by user";
        ApprovalView view = approvalApplication.reject(id, reason);
        return Result.ok(view);
    }
}
