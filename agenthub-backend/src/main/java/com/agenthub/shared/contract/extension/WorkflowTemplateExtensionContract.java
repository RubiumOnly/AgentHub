package com.agenthub.shared.contract.extension;

import com.agenthub.orchestration.domain.dsl.WorkflowDsl;

import java.util.List;

/**
 * Extension contract SPI for versioned Workflow Templates.
 * Provides Kahn DAG topological definition, quality gate thresholds, and input schemas.
 */
public interface WorkflowTemplateExtensionContract {

    String getTemplateType();

    String getTemplateVersion();

    String getDisplayName();

    String getDescription();

    WorkflowDsl getDsl();

    List<String> getRequiredContextSources();

    int getMinimumQualityGateScore();
}
