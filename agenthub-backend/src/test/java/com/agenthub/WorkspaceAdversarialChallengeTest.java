package com.agenthub;

import com.agenthub.adapter.common.BusinessException;
import com.agenthub.adapter.common.ErrorCode;
import com.agenthub.domain.workspace.service.WorkspaceResolver;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class WorkspaceAdversarialChallengeTest {

    @Autowired
    private WorkspaceResolver workspaceResolver;

    @Autowired
    private WorkspaceLockManager workspaceLockManager;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Adversarial: Deep parent traversal ../../../../Windows/System32 must be blocked")
    void testDeepTraversalVectors() {
        String attackVector = "../../../../Windows/System32";

        // Read relative
        assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", attackVector))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));

        // Write relative
        assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", attackVector))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));

        // Legacy path
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(attackVector, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));

        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(attackVector, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));
    }

    @Test
    @DisplayName("Adversarial: Escape vector default/../../sensitive.txt must be blocked")
    void testDefaultEscapingVectors() {
        String attackVector = "default/../../sensitive.txt";

        // Legacy read
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(attackVector, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));

        // Legacy write
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(attackVector, true))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));

        // Windows backslash variant
        String backslashVector = "default\\..\\..\\sensitive.txt";
        assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(backslashVector, false))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_TRAVERSAL_DENIED));
    }

    @Test
    @DisplayName("Adversarial: External client absolute paths must be blocked")
    void testExternalAbsolutePaths() {
        String[] absoluteVectors = new String[]{
                "C:/Windows/System32",
                "C:\\Windows\\System32",
                "D:/sensitive/secrets.json",
                "/etc/passwd",
                "/var/log/syslog"
        };

        for (String vector : absoluteVectors) {
            assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath(vector, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));

            assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", vector))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode().getCode()).isIn(3003, 3004));
        }
    }

    @Test
    @DisplayName("Adversarial: .git directory tampering vectors must all be blocked")
    void testGitTamperingVectors() {
        String[] gitVectors = new String[]{
                ".git/config",
                ".git/hooks/pre-commit",
                ".GIT/config",
                ".git\\config",
                ".GIT\\hooks\\pre-commit",
                "sub/.git/config",
                "sub\\.git\\config",
                ".Git/HEAD"
        };

        for (String vector : gitVectors) {
            assertThatThrownBy(() -> workspaceResolver.resolvePathForRead("default", vector))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED));

            assertThatThrownBy(() -> workspaceResolver.resolvePathForWrite("default", vector))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED));

            assertThatThrownBy(() -> workspaceResolver.resolveLegacyPath("default/" + vector, false))
                    .isInstanceOf(BusinessException.class)
                    .satisfies(e -> assertThat(((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.WORKSPACE_GIT_ACCESS_DENIED));
        }
    }

    @Test
    @DisplayName("Adversarial: WorkspaceLockManager normalization between / and \\ and case sensitivity")
    void testWorkspaceLockManagerNormalization() throws Exception {
        String pathSlash = "repo/project-a";
        String pathBackslash = "repo\\project-a";

        // 1. Thread 1 acquires lock using forward slash
        boolean locked = workspaceLockManager.tryLock(pathSlash, 100);
        assertThat(locked).isTrue();

        AtomicBoolean thread2Acquired = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);

        // 2. Thread 2 attempts to acquire lock using backslash - must fail due to lock contention!
        Thread thread2 = new Thread(() -> {
            boolean acquired = workspaceLockManager.tryLock(pathBackslash, 100);
            thread2Acquired.set(acquired);
            if (acquired) {
                workspaceLockManager.unlock(pathBackslash);
            }
            latch.countDown();
        });

        thread2.start();
        latch.await(2, TimeUnit.SECONDS);

        assertThat(thread2Acquired.get()).isFalse();

        // 3. Thread 1 unlocks using forward slash
        workspaceLockManager.unlock(pathSlash);

        // 4. Now Thread 2 (or main thread) should be able to acquire using backslash!
        boolean lockedBackslash = workspaceLockManager.tryLock(pathBackslash, 500);
        assertThat(lockedBackslash).isTrue();
        workspaceLockManager.unlock(pathBackslash);

        // 5. Case insensitivity check
        String upperPath = "REPO/PROJECT-A";
        boolean lockedUpper = workspaceLockManager.tryLock(upperPath, 100);
        assertThat(lockedUpper).isTrue();

        AtomicBoolean thread3Acquired = new AtomicBoolean(false);
        CountDownLatch latch2 = new CountDownLatch(1);
        Thread thread3 = new Thread(() -> {
            boolean acquired = workspaceLockManager.tryLock(pathSlash, 100);
            thread3Acquired.set(acquired);
            if (acquired) {
                workspaceLockManager.unlock(pathSlash);
            }
            latch2.countDown();
        });
        thread3.start();
        latch2.await(2, TimeUnit.SECONDS);

        assertThat(thread3Acquired.get()).isFalse();
        workspaceLockManager.unlock(upperPath);
    }

    @Test
    @DisplayName("Adversarial: Web REST endpoints penetration test")
    void testWebControllerPenetration() throws Exception {
        // Deep traversal
        mockMvc.perform(get("/api/workspace/files").param("path", "../../../../Windows/System32"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));

        // default/../../sensitive.txt
        mockMvc.perform(get("/api/workspace/file/content").param("filePath", "default/../../sensitive.txt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3004));

        // .git\config via web
        mockMvc.perform(get("/api/workspace/file/content").param("filePath", "default/.git\\config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        // .GIT/config via web
        mockMvc.perform(get("/api/workspace/file/content").param("filePath", "default/.GIT/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        // .git/hooks/pre-commit save
        mockMvc.perform(post("/api/workspace/file/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filePath\":\"default/.git/hooks/pre-commit\",\"content\":\"rm -rf /\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));

        // .git\\hooks\\pre-commit save
        mockMvc.perform(post("/api/workspace/file/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"filePath\":\"default/.git\\\\hooks\\\\pre-commit\",\"content\":\"rm -rf /\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3005));
    }
}
