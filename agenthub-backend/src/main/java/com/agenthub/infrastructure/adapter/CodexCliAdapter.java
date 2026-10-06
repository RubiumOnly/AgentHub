package com.agenthub.infrastructure.adapter;

import com.agenthub.domain.agent.model.AgentPlatformType;
import org.springframework.stereotype.Component;

@Component
public class CodexCliAdapter extends CliProcessAdapter {

    @Override
    public AgentPlatformType getSupportedPlatform() {
        return AgentPlatformType.CODEX;
    }

    @Override
    protected String getCommandName() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "codex.cmd" : "codex";
    }
}
