package com.agenthub.agent.domain.evaluation;

import java.time.LocalDateTime;
import java.util.List;

public class EvaluationReportView {

    private String reportId;
    private String suiteVersion;
    private String baselineModel;
    private double passRate;
    private double toolMisuseRate;
    private double diffAcceptanceRate;
    private int totalTokenCost;
    private double humanInterventionRate;
    private List<EvaluationTaskResult> taskResults;
    private LocalDateTime evaluatedAt;

    public EvaluationReportView() {}

    public EvaluationReportView(String reportId,
                                String suiteVersion,
                                String baselineModel,
                                double passRate,
                                double toolMisuseRate,
                                double diffAcceptanceRate,
                                int totalTokenCost,
                                double humanInterventionRate,
                                List<EvaluationTaskResult> taskResults,
                                LocalDateTime evaluatedAt) {
        this.reportId = reportId;
        this.suiteVersion = suiteVersion;
        this.baselineModel = baselineModel;
        this.passRate = passRate;
        this.toolMisuseRate = toolMisuseRate;
        this.diffAcceptanceRate = diffAcceptanceRate;
        this.totalTokenCost = totalTokenCost;
        this.humanInterventionRate = humanInterventionRate;
        this.taskResults = taskResults;
        this.evaluatedAt = evaluatedAt;
    }

    public String getReportId() { return reportId; }
    public void setReportId(String reportId) { this.reportId = reportId; }
    public String getSuiteVersion() { return suiteVersion; }
    public void setSuiteVersion(String suiteVersion) { this.suiteVersion = suiteVersion; }
    public String getBaselineModel() { return baselineModel; }
    public void setBaselineModel(String baselineModel) { this.baselineModel = baselineModel; }
    public double getPassRate() { return passRate; }
    public void setPassRate(double passRate) { this.passRate = passRate; }
    public double getToolMisuseRate() { return toolMisuseRate; }
    public void setToolMisuseRate(double toolMisuseRate) { this.toolMisuseRate = toolMisuseRate; }
    public double getDiffAcceptanceRate() { return diffAcceptanceRate; }
    public void setDiffAcceptanceRate(double diffAcceptanceRate) { this.diffAcceptanceRate = diffAcceptanceRate; }
    public int getTotalTokenCost() { return totalTokenCost; }
    public void setTotalTokenCost(int totalTokenCost) { this.totalTokenCost = totalTokenCost; }
    public double getHumanInterventionRate() { return humanInterventionRate; }
    public void setHumanInterventionRate(double humanInterventionRate) { this.humanInterventionRate = humanInterventionRate; }
    public List<EvaluationTaskResult> getTaskResults() { return taskResults; }
    public void setTaskResults(List<EvaluationTaskResult> taskResults) { this.taskResults = taskResults; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(LocalDateTime evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
