package com.agenthub.project.dto.context;

import com.agenthub.project.domain.context.ContextSourceType;
import com.agenthub.project.domain.context.SecurityLevel;

import java.util.Map;

/**
 * Command to register or refresh a versioned Context Source.
 */
public class RegisterContextSourceCommand {

    private ContextSourceType sourceType = ContextSourceType.PROJECT_RULES;
    private String name;
    private String filePath;
    private String content;
    private String commitHash;
    private SecurityLevel securityLevel = SecurityLevel.INTERNAL;
    private Map<String, String> metadata;

    public RegisterContextSourceCommand() {}

    public RegisterContextSourceCommand(ContextSourceType sourceType, String name, String filePath,
                                        String content, String commitHash, SecurityLevel securityLevel,
                                        Map<String, String> metadata) {
        this.sourceType = sourceType;
        this.name = name;
        this.filePath = filePath;
        this.content = content;
        this.commitHash = commitHash;
        this.securityLevel = securityLevel != null ? securityLevel : SecurityLevel.INTERNAL;
        this.metadata = metadata;
    }

    public ContextSourceType getSourceType() { return sourceType; }
    public void setSourceType(ContextSourceType sourceType) { this.sourceType = sourceType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public SecurityLevel getSecurityLevel() { return securityLevel; }
    public void setSecurityLevel(SecurityLevel securityLevel) { this.securityLevel = securityLevel; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
}
