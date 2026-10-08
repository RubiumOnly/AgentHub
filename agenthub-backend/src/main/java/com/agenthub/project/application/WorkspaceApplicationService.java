package com.agenthub.project.application;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowDefinition;
import com.agenthub.domain.workflow.model.WorkflowModels.WorkflowExecutionResult;
import com.agenthub.domain.workflow.service.WorkflowEngineService;
import com.agenthub.domain.workspace.model.FileDiffEntry;
import com.agenthub.domain.workspace.model.StructuredDiff;
import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkspaceApplicationService implements WorkspaceApplication {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceApplicationService.class);
    private static final long MAX_WRITE_BYTES = 10 * 1024 * 1024L; // 10 MB
    private static final long MAX_PREVIEW_BYTES = 2 * 1024 * 1024L; // 2 MB

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    private final JGitWorkspaceManager gitManager;
    private final WorkflowEngineService workflowEngineService;
    private final WorkspaceResolver workspaceResolver;

    public WorkspaceApplicationService(JGitWorkspaceManager gitManager,
                                      WorkflowEngineService workflowEngineService,
                                      WorkspaceResolver workspaceResolver) {
        this.gitManager = gitManager;
        this.workflowEngineService = workflowEngineService;
        this.workspaceResolver = workspaceResolver;
    }

    @Override
    public StructuredDiff computeStructuredDiff(String workspaceIdOrPath) throws Exception {
        Path safeDir = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        return gitManager.computeStructuredDiff(safeDir.toFile());
    }

    @Override
    public List<FileDiffEntry> computeDiff(String path) throws Exception {
        return computeStructuredDiff(path).getEntries();
    }

    @Override
    public List<WorkspaceFileNode> listFiles(String workspaceIdOrPath) {
        return listFiles(workspaceIdOrPath, "");
    }

    @Override
    public List<WorkspaceFileNode> listFiles(String workspaceIdOrPath, String relativeDirectoryPath) {
        Path root = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        Path targetDir = root;
        if (relativeDirectoryPath != null && !relativeDirectoryPath.isBlank()) {
            targetDir = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativeDirectoryPath);
        }

        File current = targetDir.toFile();
        List<WorkspaceFileNode> fileNodes = new ArrayList<>();
        if (!current.exists() || !current.isDirectory()) {
            return fileNodes;
        }

        scanFiles(current, root.toFile(), fileNodes);
        return fileNodes;
    }

    @Override
    public String getFileContent(String filePath) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, false);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + filePath);
        }
        if (file.length() > MAX_PREVIEW_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum preview limit of 2MB: " + file.length() + " bytes");
        }
        if (JGitWorkspaceManager.isBinaryFile(file)) {
            throw new BusinessException(ErrorCode.WORKSPACE_BINARY_PREVIEW_DENIED,
                    "Binary file preview is not supported: " + file.getName());
        }
        return Files.readString(safeFile, StandardCharsets.UTF_8);
    }

    @Override
    public String getFileContent(String workspaceIdOrPath, String relativePath) throws Exception {
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }
        if (file.length() > MAX_PREVIEW_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum preview limit of 2MB: " + file.length() + " bytes");
        }
        if (JGitWorkspaceManager.isBinaryFile(file)) {
            throw new BusinessException(ErrorCode.WORKSPACE_BINARY_PREVIEW_DENIED,
                    "Binary file preview is not supported: " + file.getName());
        }
        return Files.readString(safeFile, StandardCharsets.UTF_8);
    }

    @Override
    public WorkspaceFileDetailView getFileDetail(String workspaceIdOrPath, String relativePath) throws Exception {
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }

        boolean isBinary = JGitWorkspaceManager.isBinaryFile(file);
        long size = file.length();
        long lastModified = file.lastModified();
        String name = file.getName();

        if (isBinary) {
            return new WorkspaceFileDetailView(name, relativePath, null, true, size, false, lastModified);
        }

        if (size > MAX_PREVIEW_BYTES) {
            // Read first 2MB with truncation flag
            byte[] bytes = new byte[(int) MAX_PREVIEW_BYTES];
            try (var in = Files.newInputStream(safeFile)) {
                int read = in.read(bytes);
                String truncatedContent = new String(bytes, 0, Math.max(0, read), StandardCharsets.UTF_8);
                return new WorkspaceFileDetailView(name, relativePath, truncatedContent, false, size, true, lastModified);
            }
        }

        String content = Files.readString(safeFile, StandardCharsets.UTF_8);
        return new WorkspaceFileDetailView(name, relativePath, content, false, size, false, lastModified);
    }

    @Override
    public void saveFile(String filePath, String content) throws Exception {
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, true);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void saveFile(String workspaceIdOrPath, String relativePath, String content) throws Exception {
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, relativePath);
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }

    @Override
    public void renameFile(String workspaceIdOrPath, String oldRelativePath, String newRelativePath) throws Exception {
        Path src = workspaceResolver.resolvePathForRead(workspaceIdOrPath, oldRelativePath);
        Path dest = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, newRelativePath);

        if (!Files.exists(src)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Source file not found: " + oldRelativePath);
        }
        if (Files.exists(dest)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Target file already exists: " + newRelativePath);
        }
        if (dest.getParent() != null && !Files.exists(dest.getParent())) {
            Files.createDirectories(dest.getParent());
        }
        Files.move(src, dest);
        log.info("Renamed file in workspace [{}] from [{}] to [{}]", workspaceIdOrPath, oldRelativePath, newRelativePath);
    }

    @Override
    public void deleteFile(String workspaceIdOrPath, String relativePath) throws Exception {
        Path target = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, relativePath);
        if (!Files.exists(target)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }

        if (Files.isDirectory(target)) {
            try (var stream = Files.walk(target)) {
                stream.sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
            }
        } else {
            Files.delete(target);
        }
        log.info("Deleted file in workspace [{}]: [{}]", workspaceIdOrPath, relativePath);
    }

    @Override
    public WorkflowExecutionResult executeWorkflow(WorkflowDefinition workflow, String workspacePath, String taskPrompt) {
        String targetPath = (workspacePath != null && !workspacePath.isBlank())
                ? workspacePath : workspaceBaseDir + "/default";
        Path safeWorkspace = workspaceResolver.resolveLegacyPath(targetPath, false);
        return workflowEngineService.executeWorkflow(workflow, safeWorkspace.toString(), taskPrompt);
    }

    private void validateWriteSize(String content) {
        if (content != null) {
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_WRITE_BYTES) {
                throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                        "File content exceeds maximum write limit of 10MB: " + bytes.length + " bytes");
            }
        }
    }

    private void scanFiles(File current, File root, List<WorkspaceFileNode> nodes) {
        File[] files = current.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.getName().equalsIgnoreCase(".git")) continue;
            String relative = root.toPath().relativize(f.toPath()).toString().replace("\\", "/");
            boolean isBinary = !f.isDirectory() && JGitWorkspaceManager.isBinaryFile(f);
            nodes.add(new WorkspaceFileNode(f.getName(), relative, f.isDirectory(), f.length(), isBinary, f.lastModified()));
            if (f.isDirectory()) {
                scanFiles(f, root, nodes);
            }
        }
    }
}
