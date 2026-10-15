package com.agenthub.shared.contract.extension;

import java.util.List;

/**
 * Extension contract SPI for versioned Agent Definitions.
 * Governs agent role, prompt template, tool allowlist, and token/budget limits.
 */
public interface AgentDefinitionExtensionContract {

    String getAgentRole();

    String getDefinitionVersion();

    String getSystemPrompt();

    List<String> getAllowedTools();

    double getDefaultBudgetUsd();

    int getMaxContextTokens();

    boolean requiresApprovalForExecution();
}
