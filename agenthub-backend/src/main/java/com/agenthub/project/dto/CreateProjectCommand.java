package com.agenthub.project.dto;

public class CreateProjectCommand {
    private String name;
    private String description;
    private String relativeRoot;

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
