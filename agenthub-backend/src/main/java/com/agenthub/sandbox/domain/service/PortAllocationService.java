package com.agenthub.sandbox.domain.service;

import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Map;
import java.util.Set;
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

    // Reserved infrastructure ports that deployments must never bind
    private static final Set<Integer> FORBIDDEN_INFRA_PORTS = Set.of(80, 443, 3000, 3306, 6379, 8080);

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
                // Verify real OS socket availability on both wildcard and loopback
                if (isSocketAvailable(candidatePort)) {
                    log.info("Allocated port [{}] for deployment [{}]", candidatePort, deploymentId);
                    return candidatePort;
                } else {
                    // Socket bound by an external process, release reservation and continue
                    allocatedPorts.remove(candidatePort, deploymentId);
                }
            }
        }

        log.error("Failed to allocate port for deployment [{}]: All ports in range [{}-{}] exhausted",
                deploymentId, minPort, maxPort);
        throw new BusinessException(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED,
                "No available ports for deployment in configured range [" + minPort + "-" + maxPort + "]");
    }

    /**
     * Atomically reserves a specific requested port after verifying it is neither reserved nor bound at the OS TCP level,
     * or dynamically allocates a free port from the pool if requestedPort is null/non-positive.
     */
    public synchronized int allocateOrReserveSpecificPort(String deploymentId, Integer requestedPort) {
        if (deploymentId == null || deploymentId.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "Deployment ID cannot be empty for port allocation");
        }
        if (requestedPort == null || requestedPort <= 0) {
            return allocatePort(deploymentId);
        }
        if (requestedPort < 1024 || requestedPort > 65535 || FORBIDDEN_INFRA_PORTS.contains(requestedPort)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR,
                    "Requested port [" + requestedPort + "] is restricted or out of valid unprivileged range [1024-65535]");
        }

        String existingOwner = allocatedPorts.putIfAbsent(requestedPort, deploymentId);
        if (existingOwner != null && !existingOwner.equals(deploymentId)) {
            throw new BusinessException(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED,
                    "Requested port [" + requestedPort + "] is already reserved by active deployment [" + existingOwner + "]");
        }

        if (!isSocketAvailable(requestedPort)) {
            allocatedPorts.remove(requestedPort, deploymentId);
            throw new BusinessException(ErrorCode.DEPLOYMENT_PORT_EXHAUSTED,
                    "Requested port [" + requestedPort + "] is already bound by an active OS TCP socket");
        }

        log.info("Reserved requested port [{}] for deployment [{}]", requestedPort, deploymentId);
        return requestedPort;
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

    /**
     * Verifies true OS TCP socket availability without SO_REUSEADDR so that active listeners
     * on either 127.0.0.1 or 0.0.0.0 on Windows/Linux are reliably detected.
     */
    public boolean isSocketAvailable(int port) {
        try (ServerSocket loopbackSocket = new ServerSocket()) {
            loopbackSocket.setReuseAddress(false);
            loopbackSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 1);
        } catch (IOException e) {
            return false;
        }
        try (ServerSocket wildcardSocket = new ServerSocket()) {
            wildcardSocket.setReuseAddress(false);
            wildcardSocket.bind(new InetSocketAddress(port), 1);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
