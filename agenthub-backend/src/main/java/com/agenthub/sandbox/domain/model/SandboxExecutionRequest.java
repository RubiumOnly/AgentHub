package com.agenthub.sandbox.domain.model;

import java.nio.file.Path;
import java.util.*;

/**
 * Encapsulates parameters for a command execution inside an isolated sandbox.
 */
public class SandboxExecutionRequest {

    private String executionId;
    private String command;
    private List<String> args;
    private Path workingDirectory;
    private Map<String, String> environment;
    private SandboxResourceQuota resourceQuota;
    private boolean readOnlyRoot;
    private String containerUser;
    private boolean networkDisabled;

    public SandboxExecutionRequest() {
        this.executionId = UUID.randomUUID().toString();
        this.args = new ArrayList<>();
        this.environment = new HashMap<>();
        this.resourceQuota = SandboxResourceQuota.defaults();
        this.readOnlyRoot = true;
        this.containerUser = "1000:1000";
        this.networkDisabled = false;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getExecutionId() { return executionId; }
    public void setExecutionId(String executionId) { this.executionId = executionId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public List<String> getArgs() { return args; }
    public void setArgs(List<String> args) { this.args = args != null ? args : new ArrayList<>(); }

    public Path getWorkingDirectory() { return workingDirectory; }
    public void setWorkingDirectory(Path workingDirectory) { this.workingDirectory = workingDirectory; }

    public Map<String, String> getEnvironment() { return environment; }
    public void setEnvironment(Map<String, String> environment) { this.environment = environment != null ? environment : new HashMap<>(); }

    public SandboxResourceQuota getResourceQuota() { return resourceQuota; }
    public void setResourceQuota(SandboxResourceQuota resourceQuota) {
        this.resourceQuota = resourceQuota != null ? resourceQuota : SandboxResourceQuota.defaults();
    }

    public boolean isReadOnlyRoot() { return readOnlyRoot; }
    public void setReadOnlyRoot(boolean readOnlyRoot) { this.readOnlyRoot = readOnlyRoot; }

    public String getContainerUser() { return containerUser; }
    public void setContainerUser(String containerUser) { this.containerUser = containerUser; }

    public boolean isNetworkDisabled() { return networkDisabled; }
    public void setNetworkDisabled(boolean networkDisabled) { this.networkDisabled = networkDisabled; }

    public static class Builder {
        private final SandboxExecutionRequest request = new SandboxExecutionRequest();

        public Builder executionId(String id) {
            request.setExecutionId(id);
            return this;
        }

        public Builder command(String command) {
            request.setCommand(command);
            return this;
        }

        public Builder args(List<String> args) {
            request.setArgs(args);
            return this;
        }

        public Builder args(String... args) {
            request.setArgs(Arrays.asList(args));
            return this;
        }

        public Builder workingDirectory(Path workingDirectory) {
            request.setWorkingDirectory(workingDirectory);
            return this;
        }

        public Builder environment(Map<String, String> environment) {
            request.setEnvironment(environment);
            return this;
        }

        public Builder resourceQuota(SandboxResourceQuota quota) {
            request.setResourceQuota(quota);
            return this;
        }

        public Builder readOnlyRoot(boolean readOnlyRoot) {
            request.setReadOnlyRoot(readOnlyRoot);
            return this;
        }

        public Builder containerUser(String user) {
            request.setContainerUser(user);
            return this;
        }

        public Builder networkDisabled(boolean disabled) {
            request.setNetworkDisabled(disabled);
            return this;
        }

        public SandboxExecutionRequest build() {
            if (request.getExecutionId() == null || request.getExecutionId().isBlank()) {
                request.setExecutionId(UUID.randomUUID().toString());
            }
            if (request.getResourceQuota() == null) {
                request.setResourceQuota(SandboxResourceQuota.defaults());
            }
            return request;
        }
    }
}
