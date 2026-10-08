package com.agenthub.orchestration.domain.approval;

import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ApprovalSignalRegistry {

    private final ConcurrentMap<String, CompletableFuture<ApprovalDecisionResult>> approvalSignals = new ConcurrentHashMap<>();

    public CompletableFuture<ApprovalDecisionResult> register(String approvalId) {
        return approvalSignals.computeIfAbsent(approvalId, k -> new CompletableFuture<>());
    }

    public boolean complete(String approvalId, ApprovalDecisionResult result) {
        CompletableFuture<ApprovalDecisionResult> future = approvalSignals.get(approvalId);
        if (future != null) {
            future.complete(result);
            return true;
        }
        return false;
    }

    public void remove(String approvalId) {
        if (approvalId != null) {
            approvalSignals.remove(approvalId);
        }
    }
}
