package com.agenthub.ops.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

/**
 * Command to trigger a point-in-time consistent disaster recovery backup snapshot.
 */
public class BackupTriggerCommand implements ContextCommand {

    private String backupDir;
    private String encryptionPassphrase;

    public BackupTriggerCommand() {}

    public BackupTriggerCommand(String backupDir, String encryptionPassphrase) {
        this.backupDir = backupDir;
        this.encryptionPassphrase = encryptionPassphrase;
    }

    @Override
    public BoundedContext boundedContext() {
        return BoundedContext.OPS;
    }

    public String getBackupDir() { return backupDir; }
    public void setBackupDir(String backupDir) { this.backupDir = backupDir; }

    public String getEncryptionPassphrase() { return encryptionPassphrase; }
    public void setEncryptionPassphrase(String encryptionPassphrase) { this.encryptionPassphrase = encryptionPassphrase; }
}
