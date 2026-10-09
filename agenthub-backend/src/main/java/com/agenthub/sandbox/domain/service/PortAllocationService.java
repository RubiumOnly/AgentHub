package com.agenthub.sandbox.domain.service;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe port allocator and lifecycle manager for local preview and isolated service deployments.
 * Verifies true TCP socket availability and tracks port-to-deployment bindings.
 */
@Service
public class PortAllocationService {

    private static final Logger log = LoggerFactory.getLogger(PortAllocationService.class);

    public static final int DEFAULT_MIN_PORT = 18000;
    public static final int DEFAULT_MAX_PORT = 18999;

    private final int minPort;
    private final int maxPort;
    private final AtomicInteger nextPortOffset = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, String> allocatedPorts = new ConcurrentHashMap<>();

    public PortAllocationService() {
        this(DEFAULT_MIN_PORT, DEFAULT_MAX_PORT);
    }

    public PortAllocationService(int minPort, int maxPort) {
        if (minPort <= 0 || maxPort <= minPort || maxPort > 65535) {
            throw new IllegalArgumentException("Invalid port range: [" + minPort + ", " + maxPort + "]");
        }
        this.minPort = minPort;
        this.maxPort = maxPort;
    }

    /**
     * Atomically allocates an available port within the configured range for the specified deployment.
     */
    public synchronized int allocatePort(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Deployment ID cannot be empty for port allocation");
        }

        int totalRange = (maxPort - minPort) + 1;
        for (int i = 0; i < totalRange; i++) {
            int offset = Math.floorMod(nextPortOffset.getAndIncrement(), totalRange);
            int candidatePort = minPort + offset;

            // Check if already reserved in memory
            if (allocatedPorts.putIfAbsent(candidatePort, deploymentId) == null) {
                // Verify real OS socket availability
                if (isSocketAvailable(candidatePort)) {
                    log.info("Allocated port [{}] for deployment [{}]", candidatePort, deploymentId);
                    return candidatePort;
                } else {
                    // Socket bound by an external process, release reservation and continue
                    allocatedPorts.remove(candidatePort);
                }
            }
        }

        log.error("Failed to allocate port for deployment [{}]: All ports in range [{}-{}] exhausted",
                deploymentId, minPort, maxPort);
        throw new BusinessException(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED,
                "No available ports for deployment in configured range [" + minPort + "-" + maxPort + "]");
    }

    /**
     * Releases a port previously allocated to a deployment.
     */
    public synchronized void releasePort(int port) {
        String deploymentId = allocatedPorts.remove(port);
        if (deploymentId != null) {
            log.info("Released port [{}] from deployment [{}]", port, deploymentId);
        }
    }

    /**
     * Releases all ports associated with a deployment.
     */
    public synchronized void releasePortForDeployment(String deploymentId) {
        if (deploymentId == null) {
            return;
        }
        allocatedPorts.entrySet().removeIf(entry -> {
            if (deploymentId.equals(entry.getValue())) {
                log.info("Released port [{}] for deployment [{}]", entry.getKey(), deploymentId);
                return true;
            }
            return false;
        });
    }

    /**
     * Checks if a port is currently allocated by this manager.
     */
    public boolean isPortAllocated(int port) {
        return allocatedPorts.containsKey(port);
    }

    /**
     * Finds the port currently allocated to a deployment, or null if none.
     */
    public Integer getAllocatedPort(String deploymentId) {
        if (deploymentId == null) {
            return null;
        }
        for (Map.Entry<Integer, String> entry : allocatedPorts.entrySet()) {
            if (deploymentId.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private boolean isSocketAvailable(int port) {
        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
