package com.agenthub.project.application.workspace;

import com.agenthub.domain.workspace.service.JGitWorkspaceManager;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.project.application.WorkspaceQuotaGuard;
import com.agenthub.project.domain.quota.WorkspaceQuotaPolicy;
import com.agenthub.project.dto.WorkspaceFileDetailView;
import com.agenthub.project.dto.WorkspaceFileNode;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 工作区文件读写缝（Phase 10 R8 自 WorkspaceApplicationService 抽出）：
 * 文件树、内容读写、重命名/删除、下载与打包；访问校验与配额校验由调用方与本缝分担
 * （读/归档前调用方做访问校验，写路径在本缝内执行配额校验）。
 */
@Component
public class WorkspaceFileOperations {

    public static final long MAX_WRITE_BYTES = 10 * 1024 * 1024L; // 10 MB
    public static final long MAX_PREVIEW_BYTES = 2 * 1024 * 1024L; // 2 MB

    private final WorkspaceResolver workspaceResolver;
    private final WorkspaceQuotaGuard quotaGuard;

    public WorkspaceFileOperations(WorkspaceResolver workspaceResolver,
                                   @Autowired(required = false) WorkspaceQuotaGuard quotaGuard) {
        this.workspaceResolver = workspaceResolver;
        this.quotaGuard = quotaGuard != null ? quotaGuard : new WorkspaceQuotaGuard();
    }

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

    public String getFileContentLegacy(String filePath) throws Exception {
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, false);
        return readFileForPreview(safeFile, filePath);
    }

    public String getFileContent(String workspaceIdOrPath, String relativePath) throws Exception {
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        return readFileForPreview(safeFile, relativePath);
    }

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

    public void saveFileLegacy(String filePath, String content) throws Exception {
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolveLegacyPath(filePath, true);
        writeContent(safeFile, content);
    }

    public void saveFile(String workspaceIdOrPath, String relativePath, String content) throws Exception {
        validateWriteSize(content);
        Path safeFile = workspaceResolver.resolvePathForWrite(workspaceIdOrPath, relativePath);
        Path wsRoot = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);

        byte[] bytes = (content != null) ? content.getBytes(StandardCharsets.UTF_8) : new byte[0];
        quotaGuard.validateFileWrite(wsRoot, safeFile, bytes, WorkspaceQuotaPolicy.defaultPolicy());

        writeContent(safeFile, content);
    }

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
    }

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
    }

    public byte[] downloadFile(String workspaceIdOrPath, String relativePath) throws Exception {
        Path safeFile = workspaceResolver.resolvePathForRead(workspaceIdOrPath, relativePath);
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + relativePath);
        }
        if (file.length() > MAX_WRITE_BYTES) {
            throw new BusinessException(ErrorCode.WORKSPACE_FILE_TOO_LARGE,
                    "File size exceeds maximum download limit of 10MB: " + file.length() + " bytes");
        }
        return Files.readAllBytes(safeFile);
    }

    public String extractFilename(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "download.bin";
        }
        Path fileName = Path.of(relativePath).getFileName();
        return fileName != null ? fileName.toString() : "download.bin";
    }

    public byte[] archiveWorkspace(String workspaceIdOrPath) throws Exception {
        Path root = workspaceResolver.getWorkspaceRoot(workspaceIdOrPath);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            try (var stream = Files.walk(root)) {
                for (Path path : stream.toList()) {
                    if (Files.isDirectory(path)) continue;
                    Path rel = root.relativize(path);
                    String relStr = rel.toString().replace('\\', '/');
                    if (relStr.startsWith(".git/") || relStr.equals(".git")) continue;
                    zos.putNextEntry(new ZipEntry(relStr));
                    Files.copy(path, zos);
                    zos.closeEntry();
                }
            }
        }
        return baos.toByteArray();
    }

    public void validateWriteSize(String content) {
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

    private String readFileForPreview(Path safeFile, String displayName) throws Exception {
        File file = safeFile.toFile();
        if (!file.exists() || file.isDirectory()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found: " + displayName);
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

    private void writeContent(Path safeFile, String content) throws Exception {
        if (safeFile.getParent() != null && !Files.exists(safeFile.getParent())) {
            Files.createDirectories(safeFile.getParent());
        }
        Files.writeString(safeFile, content != null ? content : "", StandardCharsets.UTF_8);
    }
}
