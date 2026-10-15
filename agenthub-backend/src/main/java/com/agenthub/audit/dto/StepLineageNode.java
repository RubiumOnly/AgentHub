package com.agenthub.audit.dto;

import java.util.List;
import java.util.Map;

public class StepLineageNode {
    private String stepRunId;
    private String stepId;
    private String status;
    private String artifactId;
    private String commitHash;
    private String tagName;
    private List<String> changedFiles;
    private Map<String, String> fileChecksums;
    private String reviewStatus;
    private String approvalId;
    private String approvalStatus;

    public StepLineageNode() {}

    public StepLineageNode(String stepRunId, String stepId, String status, String artifactId,
                           String commitHash, String tagName, List<String> changedFiles,
                           Map<String, String> fileChecksums, String reviewStatus,
                           String approvalId, String approvalStatus) {
        this.stepRunId = stepRunId;
        this.stepId = stepId;
        this.status = status;
        this.artifactId = artifactId;
        this.commitHash = commitHash;
        this.tagName = tagName;
        this.changedFiles = changedFiles;
        this.fileChecksums = fileChecksums;
        this.reviewStatus = reviewStatus;
        this.approvalId = approvalId;
        this.approvalStatus = approvalStatus;
    }

    public String getStepRunId() { return stepRunId; }
    public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }

    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }

    public List<String> getChangedFiles() { return changedFiles; }
    public void setChangedFiles(List<String> changedFiles) { this.changedFiles = changedFiles; }

    public Map<String, String> getFileChecksums() { return fileChecksums; }
    public void setFileChecksums(Map<String, String> fileChecksums) { this.fileChecksums = fileChecksums; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getApprovalStatus() { return approvalStatus; }
    public void setApprovalStatus(String approvalStatus) { this.approvalStatus = approvalStatus; }
}
