package com.agenthub.audit.infrastructure.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "artifacts")
public class ArtifactEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "step_run_id", length = 64)
    private String stepRunId;

    @Column(name = "artifact_type", nullable = false, length = 64)
    private String artifactType;

    @Column(name = "path_or_ref", nullable = false, length = 512)
    private String pathOrRef;

    @Column(length = 64)
    private String checksum;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ArtifactEntity() {}

    public ArtifactEntity(String id, String runId, String stepRunId, String artifactType, String pathOrRef, String checksum, String metadataJson) {
        this.id = id;
        this.runId = runId;
        this.stepRunId = stepRunId;
        this.artifactType = artifactType;
        this.pathOrRef = pathOrRef;
        this.checksum = checksum;
        this.metadataJson = metadataJson;
        this.createdAt = LocalDateTime.now();
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
