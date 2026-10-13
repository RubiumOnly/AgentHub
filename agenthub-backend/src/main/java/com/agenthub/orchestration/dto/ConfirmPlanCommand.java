package com.agenthub.orchestration.dto;

import com.fasterxml.jackson.annotation.JsonAlias;

import java.util.List;

public class ConfirmPlanCommand {

    private boolean confirmed = true;

    @JsonAlias({"allowedHighImpactTools", "authorized_tools"})
    private List<String> authorizedTools;

    @JsonAlias({"decisionNote", "reason"})
    private String comments;

    public ConfirmPlanCommand() {}

    public ConfirmPlanCommand(boolean confirmed, List<String> authorizedTools, String comments) {
        this.confirmed = confirmed;
        this.authorizedTools = authorizedTools;
        this.comments = comments;
    }

    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
    public List<String> getAuthorizedTools() { return authorizedTools; }
    public void setAuthorizedTools(List<String> authorizedTools) { this.authorizedTools = authorizedTools; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}
