package com.agenthub;

import com.agenthub.sandbox.domain.service.HealthCheckProbeService;
import com.agenthub.sandbox.domain.service.PortAllocationService;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortAllocationAndHealthCheckTest {

    @Test
    @DisplayName("端口分配 1：分配端口严格落在受控范围 [18000, 18999] 且验证真实可用")
    void shouldAllocatePortWithinRange() {
        PortAllocationService portService = new PortAllocationService(18000, 18999);
        int port = portService.allocatePort("dep-test-1");

        assertThat(port).isGreaterThanOrEqualTo(18000).isLessThanOrEqualTo(18999);
        assertThat(portService.isPortAllocated(port)).isTrue();
        assertThat(portService.getAllocatedPort("dep-test-1")).isEqualTo(port);

        // Release port
        portService.releasePort(port);
        assertThat(portService.isPortAllocated(port)).isFalse();
    }

    @Test
    @DisplayName("端口并发 2：多线程并发分配端口保持原子互斥，无冲突且全量唯一")
    void shouldConcurrentlyAllocatePortsWithoutCollision() throws InterruptedException, ExecutionException {
        PortAllocationService portService = new PortAllocationService(18100, 18200);
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<Integer> allocated = ConcurrentHashMap.newKeySet();

        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final String depId = "dep-concurrent-" + i;
            futures.add(executor.submit(() -> {
                latch.await();
                return portService.allocatePort(depId);
            }));
        }

        // Release all threads simultaneously
        latch.countDown();

        for (Future<Integer> f : futures) {
            allocated.add(f.get());
        }

        executor.shutdown();
        assertThat(allocated).hasSize(threadCount);
    }

    @Test
    @DisplayName("端口耗尽 3：当受控端口池全部占满时抛出 DEPLOYMENT_PORT_EXHAUSTED 异常")
    void shouldThrowWhenPortRangeIsExhausted() {
        // Very tight range of 2 ports
        PortAllocationService tightPortService = new PortAllocationService(18950, 18951);
        tightPortService.allocatePort("dep-1");
        tightPortService.allocatePort("dep-2");

        assertThatThrownBy(() -> tightPortService.allocatePort("dep-3"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED);
                });
    }

    @Test
    @DisplayName("健康检查探测 4：对正常运行的 HTTP 服务探测成功返回 true")
    void shouldProbeHealthyEndpointSuccessfully() throws Exception {
        // Spin up a lightweight in-process HttpServer on an ephemeral port
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/health", exchange -> {
            byte[] response = "OK".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            OutputStream os = exchange.getResponseBody();
            os.write(response);
            os.close();
        });
        server.start();

        int boundPort = server.getAddress().getPort();
        String healthUrl = "http://localhost:" + boundPort + "/health";

        try {
            HealthCheckProbeService probeService = new HealthCheckProbeService();
            boolean healthy = probeService.probe(healthUrl, 3, 200);
            assertThat(healthy).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("健康检查探测 5：对不可达或断开的服务多次重试后优雅返回 false")
    void shouldReturnFalseWhenEndpointIsUnreachable() {
        HealthCheckProbeService probeService = new HealthCheckProbeService();
        // Probe an unoccupied port
        boolean healthy = probeService.probe("http://localhost:59999/health", 2, 100);
        assertThat(healthy).isFalse();
    }
}
