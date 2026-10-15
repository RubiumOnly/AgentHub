package com.agenthub.agent.domain.provider.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/**
 * Standard Tool Call execution payload returned by LLMs (OpenAI, DeepSeek, Anthropic, Gemini).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolCall implements Serializable {

    private String id;
    private String type = "function";
    private String name;
    private String arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.type = "function";
        this.name = name;
        this.arguments = arguments != null ? arguments : "{}";
    }

    public static ToolCall of(String id, String name, String arguments) {
        return new ToolCall(id, name, arguments);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type != null ? type : "function"; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArguments() { return arguments; }
    public void setArguments(String arguments) { this.arguments = arguments != null ? arguments : "{}"; }
}
