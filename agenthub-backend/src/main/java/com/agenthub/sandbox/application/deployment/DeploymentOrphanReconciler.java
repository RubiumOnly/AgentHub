package com.agenthub.sandbox.application.deployment;

import com.agenthub.sandbox.domain.model.DeploymentStatus;
import com.agenthub.sandbox.domain.service.DeploymentLifecycleWorker;
import com.agenthub.sandbox.domain.service.PortAllocationService;
import com.agenthub.sandbox.infrastructure.entity.DeploymentEntity;
import com.agenthub.sandbox.infrastructure.repository.DeploymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 孤儿部署回收缝（Phase 10 R6 自 DeploymentApplicationService 抽出）：
 * 应用启动、定时看门狗与按需路径统一走同一回收语义。
 */
@Component
public class DeploymentOrphanReconciler {

    private static final Logger log = LoggerFactory.getLogger(DeploymentOrphanReconciler.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentLifecycleWorker lifecycleWorker;
    private final PortAllocationService portAllocationService;
    private final DeploymentLifecycleSupport lifecycleSupport;

    public DeploymentOrphanReconciler(DeploymentRepository deploymentRepository,
                                      @Autowired(required = false) DeploymentLifecycleWorker lifecycleWorker,
                                      PortAllocationService portAllocationService,
                                      DeploymentLifecycleSupport lifecycleSupport) {
        this.deploymentRepository = deploymentRepository;
        this.lifecycleWorker = lifecycleWorker;
        this.portAllocationService = portAllocationService;
        this.lifecycleSupport = lifecycleSupport;
    }

    /**
     * Reconciles orphaned deployments left in active states whose runtime process/server/container is no longer alive.
     * Automatically invoked on application startup, periodically via @Scheduled watchdog, and on-demand.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(
            initialDelayString = "${agenthub.sandbox.orphan-reconcile-initial-delay-ms:30000}",
            fixedDelayString = "${agenthub.sandbox.orphan-reconcile-interval-ms:30000}"
    )
    public int reconcileOrphanDeployments() {
        List<DeploymentEntity> candidates = deploymentRepository.findByStatusIn(DeploymentLifecycleSupport.ACTIVE_LIFECYCLE_STATUSES);
        int reconciled = 0;
        for (DeploymentEntity entity : candidates) {
            if (!lifecycleWorker.isRuntimeAlive(entity.getId())) {
                reconcileSingleOrphan(entity);
                reconciled++;
            }
        }
        if (reconciled > 0) {
            log.info("Reconciled {} orphaned deployment(s)", reconciled);
        }
        return reconciled;
    }

    /**
     * 若实体处于活跃生命周期且运行时已失活，则执行孤儿回收；供查询路径按需调用。
     */
    public void reconcileIfOrphaned(DeploymentEntity entity) {
        if (DeploymentLifecycleSupport.ACTIVE_LIFECYCLE_STATUSES.contains(entity.getStatus())
                && lifecycleWorker.hasTrackedRuntime(entity.getId())
                && !lifecycleWorker.isRuntimeAlive(entity.getId())) {
            reconcileSingleOrphan(entity);
        }
    }

    void reconcileSingleOrphan(DeploymentEntity entity) {
        log.warn("Reconciling orphaned deployment [{}] (previous status={})", entity.getId(), entity.getStatus());
        String liveLogs = lifecycleWorker.getLiveProcessLogs(entity.getId());
        lifecycleSupport.transitionState(entity, DeploymentStatus.STOP);
        lifecycleWorker.stopRuntime(entity.getId(), entity.getProcessId(), entity.getSandboxType());
        lifecycleSupport.transitionState(entity, DeploymentStatus.CLEANUP);
        if (entity.getPort() != null) {
            portAllocationService.releasePort(entity.getPort());
        }
        lifecycleSupport.transitionState(entity, DeploymentStatus.FAILED);
        entity.setTerminationReason("ORPHAN_RECONCILED");
        entity.setErrorMessage("Orphaned deployment reconciled: runtime worker or child process is no longer alive");
        entity.setFinishedAt(LocalDateTime.now());
        String prevLogs = entity.getLogOutput() != null ? entity.getLogOutput() : "";
        StringBuilder sb = new StringBuilder(prevLogs);
        if (liveLogs != null && !liveLogs.isBlank() && !prevLogs.contains(liveLogs)) {
            sb.append("\n=== PROCESS OUTPUT BEFORE EXIT ===\n").append(liveLogs);
        }
        sb.append("\n=== ORPHAN RECONCILIATION: Runtime not alive, port and process tree cleaned up ===\n");
        entity.setLogOutput(sb.toString());
        entity.setUpdatedAt(LocalDateTime.now());
        deploymentRepository.save(entity);
    }
}
