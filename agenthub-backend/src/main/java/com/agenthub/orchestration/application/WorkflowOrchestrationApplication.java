package com.agenthub.orchestration.application;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.dto.CreateWorkflowDefinitionCommand;
import com.agenthub.orchestration.dto.ValidateDslResultView;
import com.agenthub.orchestration.dto.WorkflowDefinitionView;

import java.util.List;

public interface WorkflowOrchestrationApplication {
    WorkflowDefinitionView createDefinition(CreateWorkflowDefinitionCommand cmd);
    WorkflowDefinitionView getDefinitionById(String id);
    List<WorkflowDefinitionView> listDefinitions();
    ValidateDslResultView validateDsl(WorkflowDsl dsl);
    WorkflowDsl parseAndValidateDslJson(String dslJson);
}
