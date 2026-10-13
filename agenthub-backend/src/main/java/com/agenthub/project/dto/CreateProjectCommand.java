package com.agenthub.project.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

public class CreateProjectCommand implements ContextCommand {
    private String name;
    private String description;
    private String relativeRoot;

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.PROJECT;
    }

    public CreateProjectCommand() {}

    public CreateProjectCommand(String name, String description, String relativeRoot) {
        this.name = name;
        this.description = description;
        this.relativeRoot = relativeRoot;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRelativeRoot() { return relativeRoot; }
    public void setRelativeRoot(String relativeRoot) { this.relativeRoot = relativeRoot; }
}
