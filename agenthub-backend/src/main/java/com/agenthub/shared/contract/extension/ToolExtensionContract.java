package com.agenthub.shared.contract.extension;

import java.util.Map;

/**
 * Extension contract SPI for custom Agent Tools.
 * All tools must declare category, parameters schema, timeout, permission level, and rollback support.
 */
public interface ToolExtensionContract {

    String getToolName();

    String getToolVersion();

    String getCategory();

    boolean isHighImpact();

    Map<String, Object> getParameterSchema();

    long getTimeoutSeconds();

    Map<String, Object> execute(Map<String, Object> parameters, String workspacePath);

    boolean supportsRollback();

    void rollback(Map<String, Object> parameters, String workspacePath);
}
