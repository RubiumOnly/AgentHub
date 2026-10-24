package com.agenthub.audit.application.artifact;

import com.agenthub.audit.dto.ArtifactLineageView;
import com.agenthub.audit.dto.ArtifactView;
import com.agenthub.orchestration.infrastructure.entity.ApprovalEntity;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.orchestration.infrastructure.repository.ApprovalRepository;
import com.agenthub.audit.infrastructure.repository.ArtifactRepository;
import com.agenthub.execution.infrastructure.entity.StepRunEntity;
import com.agenthub.execution.infrastructure.repository.StepRunRepository;
import com.agenthub.execution.infrastructure.entity.WorkflowRunEntity;
import com.agenthub.project.infrastructure.entity.WorkspaceEntity;
import com.agenthub.project.infrastructure.repository.WorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产物血缘组装器（Phase 10 R5 自 ArtifactApplicationService 抽出的深模块缝）。
 * 归属校验由调用方完成；本组件只负责 Run → Step → Artifact/Approval 血缘图组装。
 */
@Component
public class ArtifactLineageAssembler {

    private final ArtifactRepository artifactRepository;
    private final WorkspaceRepository workspaceRepository;
    private final StepRunRepository stepRunRepository;
    private final ApprovalRepository approvalRepository;

    public ArtifactLineageAssembler(ArtifactRepository artifactRepository,
                                    WorkspaceRepository workspaceRepository,
                                    @Autowired(required = false) StepRunRepository stepRunRepository,
                                    @Autowired(required = false) ApprovalRepository approvalRepository) {
        this.artifactRepository = artifactRepository;
        this.workspaceRepository = workspaceRepository;
        this.stepRunRepository = stepRunRepository;
        this.approvalRepository = approvalRepository;
    }

    public ArtifactLineageView assemble(WorkflowRunEntity run, List<ArtifactEntity> artifacts, List<ArtifactView> artifactViews) {
        String runId = run.getId();
        List<ArtifactEntity> ordered = artifacts;

        String baselineCommit = null;
        for (ArtifactEntity art : ordered) {
            if ("BASELINE".equalsIgnoreCase(art.getArtifactType())) {
                baselineCommit = art.getPathOrRef();
                break;
            }
        }

        WorkspaceEntity ws = workspaceRepository.findByProjectId(run.getProjectId()).orElse(null);
        if (baselineCommit == null && ws != null && ws.getGitBaselineCommit() != null) {
            baselineCommit = ws.getGitBaselineCommit();
        }

        String currentCommit = baselineCommit;
        boolean isReverted = false;
        for (ArtifactEntity art : ordered) {
            if ("REVERTED".equalsIgnoreCase(art.getReviewStatus())) {
                isReverted = true;
            }
            if ("SNAPSHOT".equalsIgnoreCase(art.getArtifactType()) && currentCommit == null) {
                currentCommit = art.getPathOrRef();
            }
        }

        List<com.agenthub.audit.dto.StepLineageNode> stepNodes = new ArrayList<>();
        if (stepRunRepository != null) {
            List<StepRunEntity> steps = stepRunRepository.findByRunIdOrderByCreatedAtAsc(runId);
            Map<String, ApprovalEntity> approvalMap = new HashMap<>();
            if (approvalRepository != null) {
                for (ApprovalEntity a : approvalRepository.findByRunId(runId)) {
                    if (a.getStepRunId() != null) {
                        approvalMap.put(a.getStepRunId(), a);
                    }
                }
            }
            Map<String, ArtifactEntity> snapshotMap = new HashMap<>();
            for (ArtifactEntity a : ordered) {
                if (a.getStepRunId() != null) {
                    snapshotMap.put(a.getStepRunId(), a);
                }
            }

            for (StepRunEntity s : steps) {
                ArtifactEntity art = snapshotMap.get(s.getId());
                ApprovalEntity appr = approvalMap.get(s.getId());
                List<String> files = art != null
                        ? ArtifactMetadataExtractor.extractFilesFromMetadata(art.getMetadataJson()) : List.of();
                Map<String, String> checksums = art != null
                        ? ArtifactMetadataExtractor.extractChecksumsFromMetadata(art.getMetadataJson()) : Map.of();

                stepNodes.add(new com.agenthub.audit.dto.StepLineageNode(
                        s.getId(),
                        s.getNodeId(),
                        s.getStatus(),
                        art != null ? art.getId() : null,
                        art != null ? art.getPathOrRef() : null,
                        "snapshot-" + s.getId(),
                        files,
                        checksums,
                        art != null ? art.getReviewStatus() : "NONE",
                        appr != null ? appr.getId() : null,
                        appr != null ? appr.getStatus() : null
                ));
            }
        }

        int totalFiles = 0;
        for (var sn : stepNodes) {
            if (sn.getChangedFiles() != null) {
                totalFiles += sn.getChangedFiles().size();
            }
        }

        return new ArtifactLineageView(
                runId,
                run.getProjectId(),
                ws != null ? ws.getId() : null,
                baselineCommit,
                currentCommit,
                stepNodes,
                artifactViews,
                artifacts.size(),
                totalFiles,
                isReverted
        );
    }
}
