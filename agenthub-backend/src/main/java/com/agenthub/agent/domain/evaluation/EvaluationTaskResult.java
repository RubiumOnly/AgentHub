package com.agenthub.agent.domain.evaluation;

public class EvaluationTaskResult {

    private String taskId;
    private EvaluationTaskCategory category;
    private String name;
    private boolean passed;
    private boolean toolMisuse;
    private boolean diffAccepted;
    private int tokenCost;
    private boolean humanInterventionRequired;
    private String details;

    public EvaluationTaskResult() {}

    public EvaluationTaskResult(String taskId,
                                EvaluationTaskCategory category,
                                String name,
                                boolean passed,
                                boolean toolMisuse,
                                boolean diffAccepted,
                                int tokenCost,
                                boolean humanInterventionRequired,
                                String details) {
        this.taskId = taskId;
        this.category = category;
        this.name = name;
        this.passed = passed;
        this.toolMisuse = toolMisuse;
        this.diffAccepted = diffAccepted;
        this.tokenCost = tokenCost;
        this.humanInterventionRequired = humanInterventionRequired;
        this.details = details;
    }

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }
    public EvaluationTaskCategory getCategory() { return category; }
    public void setCategory(EvaluationTaskCategory category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }
    public boolean isToolMisuse() { return toolMisuse; }
    public void setToolMisuse(boolean toolMisuse) { this.toolMisuse = toolMisuse; }
    public boolean isDiffAccepted() { return diffAccepted; }
    public void setDiffAccepted(boolean diffAccepted) { this.diffAccepted = diffAccepted; }
    public int getTokenCost() { return tokenCost; }
    public void setTokenCost(int tokenCost) { this.tokenCost = tokenCost; }
    public boolean isHumanInterventionRequired() { return humanInterventionRequired; }
    public void setHumanInterventionRequired(boolean humanInterventionRequired) { this.humanInterventionRequired = humanInterventionRequired; }
    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
