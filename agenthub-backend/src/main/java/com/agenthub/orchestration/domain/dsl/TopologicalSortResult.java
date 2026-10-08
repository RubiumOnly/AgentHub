package com.agenthub.orchestration.domain.dsl;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class TopologicalSortResult {
    private final List<String> sortedNodeIds;
    private final Map<String, Integer> inDegrees;
    private final Map<String, Set<String>> outgoing;
    private final Map<String, Set<String>> incoming;

    public TopologicalSortResult(List<String> sortedNodeIds,
                                 Map<String, Integer> inDegrees,
                                 Map<String, Set<String>> outgoing,
                                 Map<String, Set<String>> incoming) {
        this.sortedNodeIds = sortedNodeIds;
        this.inDegrees = inDegrees;
        this.outgoing = outgoing;
        this.incoming = incoming;
    }

    public List<String> getSortedNodeIds() { return sortedNodeIds; }
    public Map<String, Integer> getInDegrees() { return inDegrees; }
    public Map<String, Set<String>> getOutgoing() { return outgoing; }
    public Map<String, Set<String>> getIncoming() { return incoming; }
}
