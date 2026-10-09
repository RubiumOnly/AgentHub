package com.agenthub.domain.workflow.service;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import com.agenthub.domain.workflow.model.WorkflowModels.*;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Legacy workflow engine.
 * @deprecated Superceded in Phase C by {@link com.agenthub.orchestration.scheduler.DagExecutionEngine}
 * which serves as the single authoritative DAG execution and recovery engine.
 */
@Deprecated
@Service
public class WorkflowEngineService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineService.class);

    private final AgentAdapterFactory adapterFactory;
    private final WorkspaceLockManager lockManager;

    public WorkflowEngineService(AgentAdapterFactory adapterFactory, WorkspaceLockManager lockManager) {
        this.adapterFactory = adapterFactory;
        this.lockManager = lockManager;
    }

    public WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String initialTask) {
        if (workflow.getNodes() == null || workflow.getNodes().isEmpty()) {
            throw new BusinessException(ErrorCode.WORKFLOW_INVALID, "Workflow contains no nodes");
        }

        String execId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        WorkflowExecutionResult result = new WorkflowExecutionResult();
        result.setExecutionId(execId);
        result.setWorkflowId(workflow.getId());
        result.setStatus(ExecutionStatus.RUNNING);

        // Lock workspace to guarantee thread-safe file generation
        boolean locked = lockManager.tryLock(workspacePath, 5000);
        if (!locked) {
            throw new BusinessException(ErrorCode.WORKSPACE_LOCKED, "Failed to acquire workspace lock: " + workspacePath);
        }

        try {
            Map<String, NodeDefinition> nodeMap = new HashMap<>();
            for (NodeDefinition node : workflow.getNodes()) {
                nodeMap.put(node.getId(), node);
                NodeRunState state = new NodeRunState();
                state.setNodeId(node.getId());
                result.getNodeStates().put(node.getId(), state);
            }

            // Find START node
            NodeDefinition currentNode = workflow.getNodes().stream()
                    .filter(n -> n.getType() == NodeType.START)
                    .findFirst()
                    .orElse(workflow.getNodes().get(0));

            String previousOutput = initialTask != null ? initialTask : "";

            Set<String> visited = new HashSet<>();
            while (currentNode != null) {
                if (visited.contains(currentNode.getId())) {
                    log.warn("Detected loop in workflow: {}", currentNode.getId());
                    break;
                }
                visited.add(currentNode.getId());

                NodeRunState state = result.getNodeStates().get(currentNode.getId());
                state.setStatus(ExecutionStatus.RUNNING);
                long start = System.currentTimeMillis();

                if (currentNode.getType() == NodeType.START) {
                    state.setStatus(ExecutionStatus.SUCCEEDED);
                    state.setOutput("Task Initiated: " + previousOutput);
                    state.setDurationMs(System.currentTimeMillis() - start);
                } else if (currentNode.getType() == NodeType.AGENT) {
                    String prompt = (currentNode.getPromptTemplate() != null ? currentNode.getPromptTemplate() : "")
                            + "\n[Context from upstream]:\n" + previousOutput;

                    UnifiedAgentAdapter adapter = adapterFactory.getAdapter(currentNode.getAgentPlatform());
                    AgentExecutionRequest req = new AgentExecutionRequest(
                            currentNode.getId(),
                            currentNode.getAgentPlatform(),
                            workspacePath,
                            prompt
                    );

                    AgentExecutionResult agentResult = adapter.execute(req);
                    state.setDurationMs(System.currentTimeMillis() - start);

                    if (agentResult.getStatus() == AgentExecutionResult.Status.FAILED) {
                        state.setStatus(ExecutionStatus.FAILED);
                        state.setError(agentResult.getErrorDetails());
                        result.setStatus(ExecutionStatus.FAILED);
                        return result;
                    } else {
                        state.setStatus(ExecutionStatus.SUCCEEDED);
                        state.setOutput(agentResult.getOutput());
                        previousOutput = agentResult.getOutput();
                    }
                } else if (currentNode.getType() == NodeType.END) {
                    state.setStatus(ExecutionStatus.SUCCEEDED);
                    state.setOutput("Workflow Finalized.");
                    state.setDurationMs(System.currentTimeMillis() - start);
                    result.setFinalOutput(previousOutput);
                    break;
                }

                // Proceed to next node
                List<String> nextIds = currentNode.getNextNodeIds();
                if (nextIds != null && !nextIds.isEmpty()) {
                    currentNode = nodeMap.get(nextIds.get(0));
                } else {
                    currentNode = null;
                }
            }

            result.setStatus(ExecutionStatus.SUCCEEDED);
            if (result.getFinalOutput() == null) {
                result.setFinalOutput(previousOutput);
            }
            return result;
        } finally {
            lockManager.unlock(workspacePath);
        }
    }
}
