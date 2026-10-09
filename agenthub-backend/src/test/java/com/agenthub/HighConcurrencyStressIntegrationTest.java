package com.agenthub;

import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.execution.application.ExecutionApplication;
import com.agenthub.execution.domain.model.WorkflowRunStatus;
import com.agenthub.execution.dto.StartRunCommand;
import com.agenthub.execution.dto.WorkflowRunView;
import com.agenthub.infrastructure.concurrency.WorkspaceLockManager;
import com.agenthub.infrastructure.metrics.AgentHubMetricsCollector;
import com.agenthub.orchestration.domain.dsl.WorkflowDsl;
import com.agenthub.orchestration.domain.dsl.WorkflowNodeDsl;
import com.agenthub.orchestration.scheduler.DagExecutionEngine;
import com.agenthub.project.application.ProjectApplication;
import com.agenthub.project.dto.CreateProjectCommand;
import com.agenthub.project.dto.ProjectView;
import com.agenthub.shared.context.RequestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 长期计划阶段 9 终局质量门禁：高并发、竞态与极限压力性能测试。
 * 验证核心高频路径（租约锁竞争、消息单调保序总线、DAG 拓扑调度吞吐）在高并发下的稳定性与性能基线。
 */
@SpringBootTest
@ActiveProfiles("test")
public class HighConcurrencyStressIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(HighConcurrencyStressIntegrationTest.class);

    @Autowired
    private WorkspaceLockManager workspaceLockManager;

    @Autowired
    private ConversationApplication conversationApplication;

    @Autowired
    private ExecutionApplication executionApplication;

    @Autowired
    private DagExecutionEngine dagExecutionEngine;

    @Autowired
    private ProjectApplication projectApplication;

    @Autowired
    private com.agenthub.conversation.domain.service.ConversationSequenceManager sequenceManager;

    @Autowired
    private AgentHubMetricsCollector metricsCollector;

    private static final String TEST_USER = "user-1";
    private ProjectView testProject;

    @BeforeEach
    void setUp() {
        RequestContext.get().setUserId(TEST_USER);
        testProject = projectApplication.createProject(new CreateProjectCommand("Stress-Project", "并发压力测试专用项目", null));
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("压测 1：工作区高并发排他租约锁竞争 (20 线程争抢同一工作区锁，验证互斥无死锁)")
    void testHighConcurrencyWorkspaceLockCompetition(@TempDir File workspaceDir) throws Exception {
        String workspaceKey = workspaceDir.getAbsolutePath();
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger concurrentHolders = new AtomicInteger(0);
        AtomicInteger maxConcurrentHolders = new AtomicInteger(0);
        List<Long> latencies = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String ownerId = "thread-worker-" + i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.currentTimeMillis();
                    // 等待超时 100ms，租约 TTL 5000ms
                    boolean acquired = workspaceLockManager.tryAcquireLock(workspaceKey, ownerId, 100, 5000);
                    long duration = System.currentTimeMillis() - start;
                    latencies.add(duration);

                    if (acquired) {
                        int current = concurrentHolders.incrementAndGet();
                        maxConcurrentHolders.accumulateAndGet(current, Math::max);
                        try {
                            // 模拟临界区耗时
                            Thread.sleep(10);
                            successCount.incrementAndGet();
                        } finally {
                            concurrentHolders.decrementAndGet();
                            workspaceLockManager.releaseLock(workspaceKey, ownerId);
                        }
                    }
                } catch (Exception e) {
                    log.error("Lock worker error", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        long benchmarkStart = System.currentTimeMillis();
        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        long totalTime = System.currentTimeMillis() - benchmarkStart;
        log.info("Workspace lock stress test finished in {}ms, success: {}, maxConcurrentHolders: {}",
                totalTime, successCount.get(), maxConcurrentHolders.get());

        assertThat(finished).isTrue();
        // 关键断言：互斥锁同一时刻持有者绝不能大于 1
        assertThat(maxConcurrentHolders.get()).isLessThanOrEqualTo(1);
        assertThat(successCount.get()).isGreaterThan(0);
    }

    @Test
    @DisplayName("压测 2：多智能体消息总线 128 分段锁单调连续保序压测 (50 并发同时争抢序号，验证 1..50 零重复零跳号)")
    void testHighConcurrencyMessageBusMonotonicSequencing() throws Exception {
        var conv = conversationApplication.createConversation(
                new CreateConversationCommand(
                        "Stress Conversation",
                        ConversationType.DIRECT_CHAT,
                        List.of("StressAgent"),
                        testProject.getId()
                )
        );
        String convId = conv.getId();

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await(); // 确保同时并发触发
                return sequenceManager.nextSequenceNum(convId);
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        long start = System.currentTimeMillis();
        startLatch.countDown();

        List<Long> allocatedSeqs = new ArrayList<>();
        for (Future<Long> f : futures) {
            allocatedSeqs.add(f.get(10, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long duration = System.currentTimeMillis() - start;
        log.info("Message bus monotonic sequence stress test completed in {}ms, total allocated: {}", duration, allocatedSeqs.size());

        assertThat(allocatedSeqs).hasSize(threadCount);

        // 提取并排序
        List<Long> sortedSeqs = new ArrayList<>(allocatedSeqs);
        Collections.sort(sortedSeqs);

        // 严格断言：序列号从 1 到 50，步长必须为 1，无任何重复，无任何空洞跳号！
        for (int i = 0; i < threadCount; i++) {
            assertThat(sortedSeqs.get(i)).isEqualTo((long) (i + 1));
        }

        // 顺次发送消息验证落库与增量提取
        for (int i = 1; i <= 3; i++) {
            conversationApplication.sendMessage(convId, new SendMessageCommand(
                    "user-1", SenderType.USER, "顺序验证消息 #" + i
            ));
        }
        List<MessageView> msgs = conversationApplication.listMessages(convId, null, "user-1");
        assertThat(msgs).hasSize(3);
    }

    @Test
    @DisplayName("压测 3：DAG 拓扑调度批量执行吞吐与耗时基线 (并发运行 5 个独立工作流，统计 P50/P99 耗时基线)")
    void testConcurrentDagExecutionThroughputAndLatency(@TempDir File baseDir) throws Exception {
        int workflowCount = 5;
        List<CompletableFuture<WorkflowRunStatus>> futures = new ArrayList<>();
        List<Long> latencies = new CopyOnWriteArrayList<>();

        long benchmarkStart = System.currentTimeMillis();

        for (int i = 0; i < workflowCount; i++) {
            WorkflowRunView run = executionApplication.startRun(
                    new StartRunCommand(testProject.getId(), null, "压测拓扑-" + i)
            );
            String runId = run.getId();

            WorkflowDsl dsl = new WorkflowDsl("wf-perf-" + i, "性能压测 DAG " + i);
            WorkflowNodeDsl step1 = new WorkflowNodeDsl("step_1", "步骤 1", "AGENT", "MOCK", "任务 A");
            WorkflowNodeDsl step2 = new WorkflowNodeDsl("step_2", "步骤 2", "AGENT", "MOCK", "任务 B");
            dsl.setNodes(List.of(step1, step2));
            dsl.setEdges(List.of(new com.agenthub.orchestration.domain.dsl.WorkflowEdgeDsl("step_1", "step_2")));

            File wsDir = new File(baseDir, "ws-perf-" + i);
            wsDir.mkdirs();

            long runStart = System.currentTimeMillis();
            CompletableFuture<WorkflowRunStatus> f = dagExecutionEngine.executeDag(
                    runId, dsl, wsDir.getAbsolutePath(), Map.of(), 30
            ).thenApply(status -> {
                latencies.add(System.currentTimeMillis() - runStart);
                return status;
            });

            futures.add(f);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(30, TimeUnit.SECONDS);
        long totalElapsed = System.currentTimeMillis() - benchmarkStart;

        for (CompletableFuture<WorkflowRunStatus> f : futures) {
            assertThat(f.get()).isEqualTo(WorkflowRunStatus.SUCCEEDED);
        }

        latencies.sort(Long::compareTo);
        long p50 = latencies.get(latencies.size() / 2);
        long p99 = latencies.get(latencies.size() - 1);

        log.info("DAG batch performance: {} workflows executed in {}ms. P50: {}ms, P99: {}ms",
                workflowCount, totalElapsed, p50, p99);

        assertThat(totalElapsed).isLessThan(20_000);
        assertThat(p99).isLessThan(10_000);
    }
}
