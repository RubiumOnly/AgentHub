package com.agenthub.audit.dto;

import java.time.LocalDateTime;

public class ArtifactView {
    private String id;
    private String runId;
    private String stepRunId;
    private String artifactType;
    private String pathOrRef;
    private String checksum;
    private String metadataJson;
    private String reviewStatus;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    private String reviewComment;
    private LocalDateTime createdAt;

    public ArtifactView() {}

    public ArtifactView(String id, String runId, String stepRunId, String artifactType,
                        String pathOrRef, String checksum, String metadataJson,
                        String reviewStatus, String reviewedBy, LocalDateTime reviewedAt,
                        String reviewComment, LocalDateTime createdAt) {
        this.id = id;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.artifactType = artifactType;
        this.pathOrRef = pathOrRef;
        this.checksum = checksum;
        this.metadataJson = metadataJson;
        this.reviewStatus = reviewStatus;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }
    public String getStepRunId() { return stepRunId; }
    public void setStepRunId(String stepRunId) { this.stepRunId = stepRunId; }
    public String getArtifactType() { return artifactType; }
    public void setArtifactType(String artifactType) { this.artifactType = artifactType; }
    public String getPathOrRef() { return pathOrRef; }
    public void setPathOrRef(String pathOrRef) { this.pathOrRef = pathOrRef; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
