package com.agenthub.agent.dto;

import java.io.Serializable;

public class BudgetCheckResultView implements Serializable {

    private boolean allowed;
    private double currentCost;
    private double limitCost;
    private double remainingCost;
    private double budgetUtilizationPercent;
    private boolean warning;
    private String reason;

    public BudgetCheckResultView() {}

    public BudgetCheckResultView(boolean allowed,
                                 double currentCost,
                                 double limitCost,
                                 double remainingCost,
                                 double budgetUtilizationPercent,
                                 boolean warning,
                                 String reason) {
        this.allowed = allowed;
        this.currentCost = currentCost;
        this.limitCost = limitCost;
        this.remainingCost = remainingCost;
        this.budgetUtilizationPercent = budgetUtilizationPercent;
        this.warning = warning;
        this.reason = reason;
    }

    public static BudgetCheckResultView allowed(double currentCost, double limitCost, double remainingCost, double percent, boolean warning) {
        return new BudgetCheckResultView(true, currentCost, limitCost, remainingCost, percent, warning, "Budget check passed");
    }

    public static BudgetCheckResultView rejected(double currentCost, double limitCost, double remainingCost, double percent, String reason) {
        return new BudgetCheckResultView(false, currentCost, limitCost, remainingCost, percent, true, reason);
    }

    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean allowed) { this.allowed = allowed; }
    public double getCurrentCost() { return currentCost; }
    public void setCurrentCost(double currentCost) { this.currentCost = currentCost; }
    public double getLimitCost() { return limitCost; }
    public void setLimitCost(double limitCost) { this.limitCost = limitCost; }
    public double getRemainingCost() { return remainingCost; }
    public void setRemainingCost(double remainingCost) { this.remainingCost = remainingCost; }
    public double getBudgetUtilizationPercent() { return budgetUtilizationPercent; }
    public void setBudgetUtilizationPercent(double budgetUtilizationPercent) { this.budgetUtilizationPercent = budgetUtilizationPercent; }
    public boolean isWarning() { return warning; }
    public void setWarning(boolean warning) { this.warning = warning; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
