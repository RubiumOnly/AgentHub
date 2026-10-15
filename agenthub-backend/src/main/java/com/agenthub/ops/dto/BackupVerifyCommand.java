package com.agenthub.ops.dto;

import com.agenthub.shared.contract.BoundedContext;
import com.agenthub.shared.contract.ContextCommand;

/**
 * Command to verify the cryptographic and structural integrity of an existing backup archive.
 */
public class BackupVerifyCommand implements ContextCommand {

    private String backupDir;
    private String encryptionPassphrase;

    public BackupVerifyCommand() {}

    public BackupVerifyCommand(String backupDir, String encryptionPassphrase) {
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
