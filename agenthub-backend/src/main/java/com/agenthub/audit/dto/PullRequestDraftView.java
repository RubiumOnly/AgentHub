package com.agenthub.audit.dto;

import com.agenthub.audit.domain.quality.QualityGateReport;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Standardized Pull Request draft specification for controlled Git delivery.
 */
public class PullRequestDraftView implements Serializable {

    private String draftId;
    private String title;
    private String runId;
    private String artifactId;
    private String sourceBranch;
    private String targetBranch;
    private String summaryMarkdown;
    private QualityGateReport qualityGateReport;
    private int changedFilesCount;
    private int addedLines;
    private int deletedLines;
    private String auditChecksum;
    private boolean canMergeSafely;
    private LocalDateTime createdAt;

    public PullRequestDraftView() {
    }

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public String getTargetBranch() {
        return targetBranch;
    }

    public void setTargetBranch(String targetBranch) {
        this.targetBranch = targetBranch;
    }

    public String getSummaryMarkdown() {
        return summaryMarkdown;
    }

    public void setSummaryMarkdown(String summaryMarkdown) {
        this.summaryMarkdown = summaryMarkdown;
    }

    public QualityGateReport getQualityGateReport() {
        return qualityGateReport;
    }

    public void setQualityGateReport(QualityGateReport qualityGateReport) {
        this.qualityGateReport = qualityGateReport;
    }

    public int getChangedFilesCount() {
        return changedFilesCount;
    }

    public void setChangedFilesCount(int changedFilesCount) {
        this.changedFilesCount = changedFilesCount;
    }

    public int getAddedLines() {
        return addedLines;
    }

    public void setAddedLines(int addedLines) {
        this.addedLines = addedLines;
    }

    public int getDeletedLines() {
        return deletedLines;
    }

    public void setDeletedLines(int deletedLines) {
        this.deletedLines = deletedLines;
    }

    public String getAuditChecksum() {
        return auditChecksum;
    }

    public void setAuditChecksum(String auditChecksum) {
        this.auditChecksum = auditChecksum;
    }

    public boolean isCanMergeSafely() {
        return canMergeSafely;
    }

    public void setCanMergeSafely(boolean canMergeSafely) {
        this.canMergeSafely = canMergeSafely;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
