package com.agenthub.agent.application;

import com.agenthub.agent.domain.provider.model.ChatRequest;
import com.agenthub.agent.dto.*;

import java.util.List;

public interface ProviderApplication {
    List<ProviderView> listProviders();
    ProviderView getProviderById(String id);
    ProviderView createProvider(ProviderCreateCommand cmd);
    ProviderView updateProvider(String id, ProviderUpdateCommand cmd);
    void deleteProvider(String id);
    RouteDecisionView evaluateRoute(ChatRequest request);
    ProviderTestResultView testProvider(String id, String testPrompt);
}
