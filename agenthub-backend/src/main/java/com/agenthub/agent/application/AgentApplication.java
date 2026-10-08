package com.agenthub.agent.application;

import com.agenthub.agent.dto.AgentDefinitionView;
import com.agenthub.agent.dto.AgentInstanceView;
import com.agenthub.agent.dto.CreateAgentInstanceCommand;
import com.agenthub.agent.dto.ProviderView;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;

import java.util.List;
import java.util.Map;

public interface AgentApplication {
    List<AgentDefinitionView> listDefinitions();
    AgentDefinitionView getDefinitionByKey(String defKey);
    List<AgentInstanceView> listCurrentUserInstances();
    AgentInstanceView createInstance(CreateAgentInstanceCommand cmd);
    List<ProviderView> listCurrentUserProviders();
    List<Map<String, Object>> getSupportedPlatforms();
    AgentExecutionResult execute(AgentExecutionRequest request);
}
