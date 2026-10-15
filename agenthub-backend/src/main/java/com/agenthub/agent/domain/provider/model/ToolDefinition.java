package com.agenthub.agent.domain.provider.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Map;

/**
 * Standard OpenAI-compatible Tool Definition payload for LLM function calling.
 * Maps seamlessly to OpenAI, Anthropic, and Gemini tool declarations.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition implements Serializable {

    private String type = "function";
    private String name;
    private String description;
    private Map<String, Object> parameters;

    public ToolDefinition() {}

    public ToolDefinition(String name, String description, Map<String, Object> parameters) {
        this.type = "function";
        this.name = name;
        this.description = description;
        this.parameters = parameters;
    }

    public static ToolDefinition ofFunction(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition(name, description, parameters);
    }

    public static ToolDefinition function(String name, String description, Map<String, Object> parameters) {
        return ofFunction(name, description, parameters);
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type != null ? type : "function"; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
