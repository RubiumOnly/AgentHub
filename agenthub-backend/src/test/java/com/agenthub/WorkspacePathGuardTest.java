package com.agenthub;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkspacePathGuardTest {

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("测试 ../ 路径穿越在读写时被严格阻断 (错误码 3004)")
    void shouldBlockDotDotPathTraversalForReadAndWrite() {
        // 1. Read with ../ traversal
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", "../outside.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED);
                });

        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", "sub/../../secret.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                });

        // 2. Write with ../ traversal
        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", "../outside.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED);
                });

        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", "sub/../../escape.txt"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                });

        // 3. Legacy path ../ traversal
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("../outside.txt", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                });

        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/../../etc/passwd", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3004);
                });
    }

    @Test
    @DisplayName("测试工作区外部绝对路径被拦截 (错误码 3003 或 3004)")
    void shouldBlockAbsolutePathOutsideWorkspace() {
        // Read outside absolute path
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", "C:/Windows/win.ini"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    int code = ((BusinessException) e).getErrorCode().getCode();
                    assertThat(code).isIn(3003, 3004);
                });

        // Write outside absolute path
        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", "C:/Windows/system32/evil.dll"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    int code = ((BusinessException) e).getErrorCode().getCode();
                    assertThat(code).isIn(3003, 3004);
                });

        // Legacy read outside absolute path
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("C:/Windows/win.ini", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    int code = ((BusinessException) e).getErrorCode().getCode();
                    assertThat(code).isIn(3003, 3004);
                });

        // Legacy write outside absolute path
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("/etc/passwd", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    int code = ((BusinessException) e).getErrorCode().getCode();
                    assertThat(code).isIn(3003, 3004);
                });
    }

    @Test
    @DisplayName("测试 .git/config 读取被拦截 (错误码 3005)")
    void shouldBlockGitConfigRead() {
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", ".git/config"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED);
                });

        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/.git/config", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });
    }

    @Test
    @DisplayName("测试 .git/hooks/pre-commit 写入被拦截 (错误码 3005)")
    void shouldBlockGitHooksPreCommitWrite() {
        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", ".git/hooks/pre-commit"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED);
                });

        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/.git/hooks/pre-commit", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });
    }

    @Test
    @DisplayName("测试大小写不敏感 .GIT/config 及变种拦截 (错误码 3005)")
    void shouldBlockCaseInsensitiveGitAccess() {
        // Uppercase .GIT
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", ".GIT/config"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });

        // Mixed case .Git
        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", ".Git/hooks/pre-commit"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });

        // Legacy path uppercase .GIT
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/.GIT/config", false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });

        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/.gIt/hooks/pre-commit", true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(3005);
                });
    }

    @Test
    @DisplayName("测试工作区内合法文件读写正常通过")
    void shouldAllowValidInWorkspaceFileReadAndWrite() throws Exception {
        String testFileName = "src/test-valid-guard-" + System.currentTimeMillis() + ".txt";
        String testContent = "Hello from WorkspacePathGuardTest!";

        // 1. Resolve for write and write content
        Path writePath = workspaceResolver.resolvePathForWrite("default", testFileName);
        assertThat(writePath).isNotNull();
        if (writePath.getParent() != null && !Files.exists(writePath.getParent())) {
            Files.createDirectories(writePath.getParent());
        }
        Files.writeString(writePath, testContent, StandardCharsets.UTF_8);

        // 2. Resolve for read and verify content
        Path readPath = workspaceResolver.resolvePathForRead("default", testFileName);
        assertThat(readPath).isNotNull();
        assertThat(Files.exists(readPath)).isTrue();
        String readContent = Files.readString(readPath, StandardCharsets.UTF_8);
        assertThat(readContent).isEqualTo(testContent);

        // 3. Resolve legacy path and verify content
        Path legacyPath = workspaceResolver.resolveLegacyPath("default/" + testFileName, false);
        assertThat(legacyPath).isNotNull();
        assertThat(Files.readString(legacyPath, StandardCharsets.UTF_8)).isEqualTo(testContent);

        // 4. Clean up
        Files.deleteIfExists(writePath);
    }

    @Test
    @DisplayName("测试 Web 接口层面路径穿越与 .git 拦截")
    void shouldBlockTraversalAndGitAccessAtWebController() throws Exception {
        // Traversal blocked on file content read -> code 3004
        mockMvc.perform(get("/api/workspace/file/content").param("filePath", "../outside.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));

        // .git blocked on file content read -> code 3005
        mockMvc.perform(get("/api/workspace/file/content").param("filePath", "default/.git/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        // .git blocked on file save -> code 3005
        mockMvc.perform(post("/api/workspace/file/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filePath\":\"default/.git/hooks/pre-commit\",\"content\":\"echo pwned\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        // Traversal blocked on diff -> code 3004
        mockMvc.perform(get("/api/workspace/diff").param("path", "../outside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));

        // Traversal blocked on files -> code 3004
        mockMvc.perform(get("/api/workspace/files").param("path", "../outside"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));
    }
}
