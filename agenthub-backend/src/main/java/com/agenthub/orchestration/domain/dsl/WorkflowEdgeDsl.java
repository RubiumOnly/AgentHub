package com.agenthub.orchestration.domain.dsl;

import java.io.Serializable;

public class WorkflowEdgeDsl implements Serializable {
    private String id;
    private String source;
    private String target;
    private String condition;

    public WorkflowEdgeDsl() {}

    public WorkflowEdgeDsl(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public WorkflowEdgeDsl(String id, String source, String target, String condition) {
        this.id = id;
        this.source = source;
        this.target = target;
        this.condition = condition;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }
    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }
}
