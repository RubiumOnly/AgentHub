package com.agenthub.orchestration.application;

import com.agenthub.orchestration.dto.ConfirmPlanCommand;
import com.agenthub.orchestration.dto.GeneratePlanCommand;
import com.agenthub.orchestration.dto.OrchestrationPlanView;

import java.util.List;

public interface OrchestratorPlanApplication {

    OrchestrationPlanView generatePlan(GeneratePlanCommand cmd);

    OrchestrationPlanView confirmPlan(String planId, ConfirmPlanCommand cmd);

    OrchestrationPlanView rejectPlan(String planId, String reason);

    OrchestrationPlanView getPlan(String planId);

    List<OrchestrationPlanView> listPlansByProject(String projectId);

    void enforceHighImpactToolAuthorization(String planId, String toolName);
}
