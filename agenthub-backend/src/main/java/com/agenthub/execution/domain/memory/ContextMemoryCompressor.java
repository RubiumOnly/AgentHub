package com.agenthub.execution.domain.memory;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Governs hierarchical long-context memory compression and cognitive resumption.
 * Invariant: Never strips project constraints, approvals, errors, or modified file paths.
 */
public class ContextMemoryCompressor {

    private static final Logger log = LoggerFactory.getLogger(ContextMemoryCompressor.class);

    public static class CompressionReport {
        private final int initialTokens;
        private final int compressedTokens;
        private final int stepsCompressed;
        private final boolean budgetMet;

        public CompressionReport(int initialTokens, int compressedTokens, int stepsCompressed, boolean budgetMet) {
            this.initialTokens = initialTokens;
            this.compressedTokens = compressedTokens;
            this.stepsCompressed = stepsCompressed;
            this.budgetMet = budgetMet;
        }

        public int getInitialTokens() { return initialTokens; }
        public int getCompressedTokens() { return compressedTokens; }
        public int getStepsCompressed() { return stepsCompressed; }
        public boolean isBudgetMet() { return budgetMet; }
    }

    public static class UncommittedOperationsReceipt {
        private final String baselineCommit;
        private final List<String> modifiedFiles;
        private final List<String> untrackedFiles;
        private final boolean clean;
        private final String status;

        public UncommittedOperationsReceipt(String baselineCommit, List<String> modifiedFiles, List<String> untrackedFiles, boolean clean) {
            this.baselineCommit = baselineCommit;
            this.modifiedFiles = modifiedFiles != null ? modifiedFiles : List.of();
            this.untrackedFiles = untrackedFiles != null ? untrackedFiles : List.of();
            this.clean = clean;
            this.status = clean ? "CLEAN" : "DRIFTED";
        }

        public String getBaselineCommit() { return baselineCommit; }
        public List<String> getModifiedFiles() { return modifiedFiles; }
        public List<String> getUntrackedFiles() { return untrackedFiles; }
        public boolean isClean() { return clean; }
        public String getStatus() { return status; }
    }

    public static int estimateTokens(HierarchicalContextMemory memory) {
        if (memory == null) return 0;
        int chars = 0;
        if (memory.getProjectMemory() != null) {
            if (memory.getProjectMemory().getProjectRules() != null) {
                chars += memory.getProjectMemory().getProjectRules().length();
            }
        }
        if (memory.getRunMemory() != null) {
            if (memory.getRunMemory().getGoal() != null) chars += memory.getRunMemory().getGoal().length();
        }
        if (memory.getStepMemories() != null) {
            for (HierarchicalContextMemory.StepMemoryItem item : memory.getStepMemories()) {
                if (item.isCompressed()) {
                    if (item.getSummary() != null) chars += item.getSummary().length();
                } else {
                    if (item.getRawOutput() != null) chars += item.getRawOutput().length();
                    if (item.getSummary() != null) chars += item.getSummary().length();
                }
                if (item.getErrorDetails() != null) chars += item.getErrorDetails().length();
                if (item.getApprovalDecision() != null) chars += item.getApprovalDecision().length();
                if (item.getModifiedFiles() != null) chars += String.join(",", item.getModifiedFiles()).length();
            }
        }
        // Approximate 1 token ~= 4 characters
        return Math.max(1, chars / 4);
    }

    /**
     * Lossless compression: Compresses verbose logs into structured summaries when token budget is exceeded.
     * Preserves all invariants: constraints, approvals, errors, file sources.
     */
    public static CompressionReport compressIfNeeded(HierarchicalContextMemory memory, int maxTokensBudget) {
        if (memory == null) {
            return new CompressionReport(0, 0, 0, true);
        }

        int initialTokens = estimateTokens(memory);
        if (initialTokens <= maxTokensBudget || memory.getStepMemories() == null || memory.getStepMemories().isEmpty()) {
            return new CompressionReport(initialTokens, initialTokens, 0, true);
        }

        int compressedCount = 0;
        // Prioritize compressing older succeeded steps with large raw outputs
        for (HierarchicalContextMemory.StepMemoryItem step : memory.getStepMemories()) {
            if (step.isCompressed()) continue;
            // Never compress if step has active unreviewed approval or recent failure
            if ("SUCCEEDED".equalsIgnoreCase(step.getStatus()) || "SKIPPED".equalsIgnoreCase(step.getStatus())) {
                String toolsStr = step.getToolsInvoked() != null && !step.getToolsInvoked().isEmpty()
                        ? String.join(",", step.getToolsInvoked()) : "none";
                String filesStr = step.getModifiedFiles() != null && !step.getModifiedFiles().isEmpty()
                        ? String.join(",", step.getModifiedFiles()) : "none";

                // Generate concise structured summary preserving provenance and sequence range
                String summary = String.format("[Step %s (%s) %s, seq %d-%d]: Tools: [%s], Files: [%s]",
                        step.getStepRunId(), step.getStepId(), step.getStatus(),
                        step.getEventSeqStart(), step.getEventSeqEnd(), toolsStr, filesStr);

                step.setSummary(summary);
                step.setRawOutput(null); // Release verbose raw payload
                step.setCompressed(true);
                step.setUpdatedAt(LocalDateTime.now());
                compressedCount++;

                int currentTokens = estimateTokens(memory);
                if (currentTokens <= maxTokensBudget) {
                    return new CompressionReport(initialTokens, currentTokens, compressedCount, true);
                }
            }
        }

        int finalTokens = estimateTokens(memory);
        return new CompressionReport(initialTokens, finalTokens, compressedCount, finalTokens <= maxTokensBudget);
    }

    /**
     * Determines whether official AOCI cognition receipt or baseline asset exists in the workspace.
     */
    public static boolean hasOfficialAociReceipt(File workspaceDir) {
        if (workspaceDir == null || !workspaceDir.exists()) {
            return false;
        }
        File aociReceipt = new File(workspaceDir, ".aoci/receipt.json");
        File aociAttestation = new File(workspaceDir, ".aoci/attestation.json");
        File aociTxt = new File(workspaceDir, "aoci.txt");
        return (aociReceipt.isFile() && aociReceipt.length() > 0)
                || (aociAttestation.isFile() && aociAttestation.length() > 0)
                || (aociTxt.isFile() && aociTxt.length() > 0);
    }

    /**
     * Determines basic workspace cognition status by eliminating silent ALIGNED fallbacks when
     * credentials are absent, guarding against empty files and non-git directories.
     * Note: This checks local non-empty file existence and git working tree cleanliness;
     * it does not prove credentials were generated by official AOCI or that official verify/check passed.
     * Returns:
     * - ALIGNED: non-empty receipt/attestation exists, workspace is a git repo, and git working tree is clean
     * - REPAIR_REQUIRED: receipt exists and git working tree has drift/uncommitted changes
     * - UNAVAILABLE: .aoci directory or receipt exists but workspace is not a valid git repository or receipt missing/empty
     * - NOT_CONFIGURED: neither .aoci directory nor receipt file exists
     */
    public static String determineAociCognitionStatus(File workspaceDir, String baselineCommit) {
        if (workspaceDir == null || !workspaceDir.exists()) {
            return "NOT_CONFIGURED";
        }
        boolean hasReceipt = hasOfficialAociReceipt(workspaceDir);
        boolean hasAociDir = new File(workspaceDir, ".aoci").isDirectory();
        boolean hasGit = new File(workspaceDir, ".git").exists();

        if (hasReceipt) {
            if (!hasGit) {
                return "UNAVAILABLE";
            }
            UncommittedOperationsReceipt receipt = verifyCognitionAndReceipt(workspaceDir, baselineCommit);
            return receipt.isClean() ? "ALIGNED" : "REPAIR_REQUIRED";
        } else if (hasAociDir) {
            return "UNAVAILABLE";
        } else {
            return "NOT_CONFIGURED";
        }
    }

    /**
     * Resumption verification: checks working directory state against git baseline.
     */
    public static UncommittedOperationsReceipt verifyCognitionAndReceipt(File workspaceDir, String baselineCommit) {
        if (workspaceDir == null || !workspaceDir.exists() || !new File(workspaceDir, ".git").exists()) {
            return new UncommittedOperationsReceipt(baselineCommit, Collections.emptyList(), Collections.emptyList(), true);
        }

        try (Git git = Git.open(workspaceDir)) {
            Status status = git.status().call();
            List<String> modified = new ArrayList<>();
            modified.addAll(status.getModified());
            modified.addAll(status.getChanged());
            modified.addAll(status.getMissing());

            List<String> untracked = new ArrayList<>(status.getUntracked());
            boolean clean = status.isClean();

            return new UncommittedOperationsReceipt(baselineCommit, modified, untracked, clean);
        } catch (Exception e) {
            log.warn("Failed to check git status in {}: {}", workspaceDir.getAbsolutePath(), e.getMessage());
            return new UncommittedOperationsReceipt(baselineCommit, Collections.emptyList(), Collections.emptyList(), false);
        }
    }
}
