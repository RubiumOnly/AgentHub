package com.agenthub.audit.domain.quality;

import java.io.Serializable;

/**
 * Individual check result for a quality gate rule dimension.
 */
public class QualityGateCheckItem implements Serializable {

    private QualityGateRuleType ruleType;
    private QualityGateStatus status;
    private int score; // 0 to 100
    private String message;
    private long executionTimeMs;

    public QualityGateCheckItem() {
    }

    public QualityGateCheckItem(QualityGateRuleType ruleType, QualityGateStatus status, int score, String message, long executionTimeMs) {
        this.ruleType = ruleType;
        this.status = status;
        this.score = score;
        this.message = message;
        this.executionTimeMs = executionTimeMs;
    }

    public QualityGateRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(QualityGateRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public QualityGateStatus getStatus() {
        return status;
    }

    public void setStatus(QualityGateStatus status) {
        this.status = status;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }
}
