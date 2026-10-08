package com.agenthub.orchestration.dto;

import java.util.List;

public class ValidateDslResultView {
    private boolean valid;
    private String message;
    private List<String> topologicalOrder;
    private int nodeCount;

    public ValidateDslResultView() {}

    public ValidateDslResultView(boolean valid, String message, List<String> topologicalOrder, int nodeCount) {
        this.valid = valid;
        this.message = message;
        this.topologicalOrder = topologicalOrder;
        this.nodeCount = nodeCount;
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public List<String> getTopologicalOrder() { return topologicalOrder; }
    public void setTopologicalOrder(List<String> topologicalOrder) { this.topologicalOrder = topologicalOrder; }
    public int getNodeCount() { return nodeCount; }
    public void setNodeCount(int nodeCount) { this.nodeCount = nodeCount; }
}
