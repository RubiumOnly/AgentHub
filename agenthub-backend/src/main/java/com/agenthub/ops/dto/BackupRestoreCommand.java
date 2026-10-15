package com.agenthub.ops.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

/**
 * Command to restore a verified backup snapshot into target workspace directories and database.
 */
public class BackupRestoreCommand implements ContextCommand {

    private String backupDir;
    private String targetWorkspaceRoot;
    private String encryptionPassphrase;

    public BackupRestoreCommand() {}

    public BackupRestoreCommand(String backupDir, String targetWorkspaceRoot, String encryptionPassphrase) {
        this.backupDir = backupDir;
        this.targetWorkspaceRoot = targetWorkspaceRoot;
        this.encryptionPassphrase = encryptionPassphrase;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.OPS;
    }

    public String getBackupDir() { return backupDir; }
    public void setBackupDir(String backupDir) { this.backupDir = backupDir; }

    public String getTargetWorkspaceRoot() { return targetWorkspaceRoot; }
    public void setTargetWorkspaceRoot(String targetWorkspaceRoot) { this.targetWorkspaceRoot = targetWorkspaceRoot; }

    public String getEncryptionPassphrase() { return encryptionPassphrase; }
    public void setEncryptionPassphrase(String encryptionPassphrase) { this.encryptionPassphrase = encryptionPassphrase; }
}
