package com.agenthub.domain.agent.model;

public enum AgentPlatformType {
    CLAUDE_CODE("claude-code", "Anthropic Claude Code CLI"),
    OPENCLAW("openclaw", "OpenClaw Agent Runtime"),
    CODEX("codex", "OpenAI Codex CLI"),
    HERMES("hermes", "Hermes Agent CLI"),
    OPENCODE("opencode", "OpenCode CLI"),
    SPRING_AI_API("spring-ai-api", "Direct LLM API Function Calling");

    private final String code;
    private final String description;

    AgentPlatformType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static AgentPlatformType fromCode(String code) {
        for (AgentPlatformType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OPENCLAW;
    }
}
