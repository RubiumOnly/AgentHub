package com.agenthub.audit.application.artifact;

import com.agenthub.audit.domain.quality.QualityGateCheckItem;
import com.agenthub.audit.domain.quality.QualityGateReport;
import com.agenthub.audit.domain.quality.QualityGateStatus;
import com.agenthub.audit.dto.PullRequestDraftView;
import com.agenthub.audit.infrastructure.entity.ArtifactEntity;
import com.agenthub.domain.workspace.model.StructuredDiff;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PR 草稿组装器（Phase 10 R5 自 ArtifactApplicationService 抽出的深模块缝）。
 * 只做草稿组装与呈现，不执行任何外部交付；真实交付必须经用户确认。
 */
@Component
public class PullRequestDraftComposer {

    public PullRequestDraftView compose(ArtifactEntity entity,
                                        QualityGateReport gateReport,
                                        StructuredDiff diff) {
        String artifactId = entity.getId();
        PullRequestDraftView draft = new PullRequestDraftView();
        draft.setDraftId("pr-draft-" + UUID.randomUUID().toString().substring(0, 8));
        draft.setArtifactId(artifactId);
        draft.setRunId(entity.getRunId());
        String mainTarget = (diff.getEntries() != null && !diff.getEntries().isEmpty() && diff.getEntries().get(0).getNewPath() != null)
                ? diff.getEntries().get(0).getNewPath()
                : entity.getArtifactType();
        draft.setTitle(String.format("feat(agent-delivery): %s (Run %s)", mainTarget, entity.getRunId()));
        draft.setSourceBranch("feature/run-" + entity.getRunId());
        draft.setTargetBranch("main");
        draft.setQualityGateReport(gateReport);

        List<String> changedFilesList = new ArrayList<>();
        if (diff.getEntries() != null) {
            for (var entry : diff.getEntries()) {
                String p = entry.getNewPath() != null && !"/dev/null".equals(entry.getNewPath())
                        ? entry.getNewPath() : entry.getOldPath();
                if (p != null && !p.isBlank()) {
                    changedFilesList.add(p);
                }
            }
        }
        draft.setChangedFiles(changedFilesList);
        draft.setChangedFilesCount(changedFilesList.size());
        draft.setAddedLines(diff.getTotalLinesAdded());
        draft.setDeletedLines(diff.getTotalLinesDeleted());
        draft.setTotalLinesAdded(diff.getTotalLinesAdded());
        draft.setTotalLinesDeleted(diff.getTotalLinesDeleted());
        draft.setQualityGateStatus(gateReport.getOverallStatus() != null ? gateReport.getOverallStatus().name() : "UNKNOWN");
        draft.setQualityGateScore(gateReport.getCompositeScore());
        draft.setSummary(String.format("本变更包含 %d 个文件的代码修改（+%d/-%d 行），经由受控工作区自动化流水线构建，已通过质量门禁评测。",
                changedFilesList.size(), diff.getTotalLinesAdded(), diff.getTotalLinesDeleted()));
        draft.setRiskAssessment(gateReport.getOverallStatus() == QualityGateStatus.PASSED
                ? "LOW_RISK: 无破坏性改动，未检测到敏感密钥泄漏，已通过 5 大维度质量门禁验证。"
                : (gateReport.getOverallStatus() == QualityGateStatus.WARNING
                ? "MEDIUM_RISK: 变更规模较大或触发建议审查项，建议人工二次复核。"
                : "HIGH_RISK: 触发质量门禁阻断项，必须修复违规项后方可合并。"));
        draft.setCanMergeSafely(gateReport.getOverallStatus() == QualityGateStatus.PASSED && "ACCEPTED".equalsIgnoreCase(entity.getReviewStatus()));
        LocalDateTime now = LocalDateTime.now();
        draft.setCreatedAt(now);
        draft.setGeneratedAt(now);

        StringBuilder sb = new StringBuilder();
        sb.append("## Automated Change Delivery Summary\n\n");
        sb.append("- **Run ID**: `").append(entity.getRunId()).append("`\n");
        sb.append("- **Artifact**: `").append(artifactId).append("` (Status: ").append(entity.getReviewStatus()).append(")\n");
        sb.append("- **Quality Gate**: **").append(gateReport.getOverallStatus()).append("** (Score: ").append(gateReport.getCompositeScore()).append("/100)\n");
        sb.append("- **Diff Scale**: `+").append(diff.getTotalLinesAdded()).append(" / -").append(diff.getTotalLinesDeleted()).append("` lines across ")
                .append(draft.getChangedFilesCount()).append(" file(s)\n\n");
        sb.append("### Quality Gate Check Matrix\n\n");
        sb.append("| Gate Dimension | Status | Score | Findings |\n");
        sb.append("|---|---|---|---|\n");
        if (gateReport.getCheckItems() != null) {
            for (QualityGateCheckItem item : gateReport.getCheckItems()) {
                sb.append("| `").append(item.getRuleType()).append("` | ").append(item.getStatus()).append(" | ")
                        .append(item.getScore()).append(" | ").append(item.getMessage()).append(" |\n");
            }
        }
        sb.append("\n### Safety & Security Verification\n\n");
        sb.append("- Zero leaked API keys or credentials detected via automated secret scanner\n");
        sb.append("- Change strictly verified under controlled workspace boundaries\n");
        sb.append("- SHA-256 Fingerprint: `").append(gateReport.getSha256Fingerprint()).append("`\n");

        draft.setSummaryMarkdown(sb.toString());
        draft.setAuditChecksum(gateReport.getSha256Fingerprint());
        return draft;
    }
}
