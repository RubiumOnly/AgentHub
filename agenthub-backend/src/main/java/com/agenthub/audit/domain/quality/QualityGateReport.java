package com.agenthub.audit.domain.quality;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Composite quality gate evaluation report with cryptographic SHA-256 fingerprint.
 */
public class QualityGateReport implements Serializable {

    private String reportId;
    private String artifactId;
    private String runId;
    private QualityGateStatus overallStatus;
    private int totalRules;
    private int passedRules;
    private int failedRules;
    private int compositeScore; // 0 to 100
    private List<QualityGateCheckItem> checkItems = new ArrayList<>();
    private LocalDateTime evaluatedAt;
    private String sha256Fingerprint;

    public QualityGateReport() {
    }

    public String getReportId() {
        return reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public QualityGateStatus getOverallStatus() {
        return overallStatus;
    }

    public void setOverallStatus(QualityGateStatus overallStatus) {
        this.overallStatus = overallStatus;
    }

    public int getTotalRules() {
        return totalRules;
    }

    public void setTotalRules(int totalRules) {
        this.totalRules = totalRules;
    }

    public int getPassedRules() {
        return passedRules;
    }

    public void setPassedRules(int passedRules) {
        this.passedRules = passedRules;
    }

    public int getFailedRules() {
        return failedRules;
    }

    public void setFailedRules(int failedRules) {
        this.failedRules = failedRules;
    }

    public int getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(int compositeScore) {
        this.compositeScore = compositeScore;
    }

    public List<QualityGateCheckItem> getCheckItems() {
        return checkItems;
    }

    public void setCheckItems(List<QualityGateCheckItem> checkItems) {
        this.checkItems = checkItems;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public String getSha256Fingerprint() {
        return sha256Fingerprint;
    }

    public void setSha256Fingerprint(String sha256Fingerprint) {
        this.sha256Fingerprint = sha256Fingerprint;
    }
}
