package com.agenthub.infrastructure.adapter;

import com.agenthub.domain.agent.model.AgentPlatformType;
import org.springframework.stereotype.Component;

@Component
public class OpenClawCliAdapter extends CliProcessAdapter {

    @Override
    public AgentPlatformType getSupportedPlatform() {
        return AgentPlatformType.OPENCLAW;
    }

    @Override
    protected String getCommandName() {
        return System.getProperty("os.name").toLowerCase().contains("win") ? "openclaw.cmd" : "openclaw";
    }
}
