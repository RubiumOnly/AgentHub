package com.agenthub.domain.workspace.model;

import java.io.Serializable;

public class FileDiffEntry implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum ChangeType {
        ADD,
        MODIFY,
        DELETE,
        RENAME,
        COPY
    }

    public enum ReviewStatus {
        PENDING,
        ACCEPTED,
        REJECTED,
        REVERTED
    }

    private String oldPath;
    private String newPath;
    private ChangeType changeType;
    private String diffContent;
    private int linesAdded;
    private int linesDeleted;
    private boolean binary;
    private boolean rename;
    private boolean hasConflict;
    private ReviewStatus reviewStatus = ReviewStatus.PENDING;
    private String checksum;

    public FileDiffEntry() {}

    public FileDiffEntry(String oldPath, String newPath, ChangeType changeType, String diffContent, int linesAdded, int linesDeleted) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.changeType = changeType;
        this.diffContent = diffContent;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.binary = false;
        this.rename = (changeType == ChangeType.RENAME);
        this.hasConflict = false;
        this.reviewStatus = ReviewStatus.PENDING;
    }

    public FileDiffEntry(String oldPath, String newPath, ChangeType changeType, String diffContent,
                         int linesAdded, int linesDeleted, boolean binary, boolean rename,
                         boolean hasConflict, ReviewStatus reviewStatus, String checksum) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.changeType = changeType;
        this.diffContent = diffContent;
        this.linesAdded = linesAdded;
        this.linesDeleted = linesDeleted;
        this.binary = binary;
        this.rename = rename;
        this.hasConflict = hasConflict;
        this.reviewStatus = reviewStatus != null ? reviewStatus : ReviewStatus.PENDING;
        this.checksum = checksum;
    }

    public String getOldPath() { return oldPath; }
    public void setOldPath(String oldPath) { this.oldPath = oldPath; }
    public String getNewPath() { return newPath; }
    public void setNewPath(String newPath) { this.newPath = newPath; }
    public ChangeType getChangeType() { return changeType; }
    public void setChangeType(ChangeType changeType) { this.changeType = changeType; }
    public String getDiffContent() { return diffContent; }
    public void setDiffContent(String diffContent) { this.diffContent = diffContent; }
    public int getLinesAdded() { return linesAdded; }
    public void setLinesAdded(int linesAdded) { this.linesAdded = linesAdded; }
    public int getLinesDeleted() { return linesDeleted; }
    public void setLinesDeleted(int linesDeleted) { this.linesDeleted = linesDeleted; }
    public boolean isBinary() { return binary; }
    public void setBinary(boolean binary) { this.binary = binary; }
    public boolean isRename() { return rename; }
    public void setRename(boolean rename) { this.rename = rename; }
    public boolean isHasConflict() { return hasConflict; }
    public void setHasConflict(boolean hasConflict) { this.hasConflict = hasConflict; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(ReviewStatus reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
