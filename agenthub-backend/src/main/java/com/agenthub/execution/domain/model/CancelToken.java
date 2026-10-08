package com.agenthub.execution.domain.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collaboration and cancellation token for Workflow Runs and Steps.
 * Supports graceful cooperative cancellation, process destruction, thread interruption,
 * and hard timeout enforcement.
 */
public class CancelToken {

    private static final Logger log = LoggerFactory.getLogger(CancelToken.class);

    private final String runId;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicBoolean timedOut = new AtomicBoolean(false);
    private final AtomicReference<String> cancelReason = new AtomicReference<>("");

    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();
    private final List<Process> registeredProcesses = new CopyOnWriteArrayList<>();
    private final List<Thread> registeredThreads = new CopyOnWriteArrayList<>();

    public CancelToken(String runId) {
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public boolean isTimedOut() {
        return timedOut.get();
    }

    public String getReason() {
        return cancelReason.get();
    }

    public void checkCancelled() {
        if (cancelled.get()) {
            throw new RunCancelledException("Workflow run [" + runId + "] was cancelled: " + cancelReason.get());
        }
    }

    public boolean cancel(String reason) {
        return doCancel(reason, false);
    }

    public boolean timeout(String reason) {
        return doCancel(reason, true);
    }

    private boolean doCancel(String reason, boolean isTimeout) {
        if (cancelled.compareAndSet(false, true)) {
            if (isTimeout) {
                timedOut.set(true);
            }
            String finalReason = reason != null && !reason.isBlank() ? reason : (isTimeout ? "Execution timed out" : "User cancelled");
            cancelReason.set(finalReason);

            log.info("Triggered cancellation for run [{}] (isTimeout={}): {}", runId, isTimeout, finalReason);

            // 1. Fire registered graceful callbacks
            for (Runnable cb : callbacks) {
                try {
                    cb.run();
                } catch (Exception e) {
                    log.warn("Error executing cancel callback for run [{}]: {}", runId, e.getMessage());
                }
            }

            // 2. Forcibly destroy registered subprocesses and their descendant trees
            for (Process p : registeredProcesses) {
                destroyProcessTree(p);
            }

            // 3. Interrupt registered worker threads
            for (Thread t : registeredThreads) {
                try {
                    if (t != null && t.isAlive() && t != Thread.currentThread()) {
                        t.interrupt();
                        log.debug("Interrupted thread [{}] for cancelled run [{}]", t.getName(), runId);
                    }
                } catch (Exception e) {
                    log.warn("Error interrupting thread for run [{}]: {}", runId, e.getMessage());
                }
            }

            return true;
        }
        return false;
    }

    public void registerCallback(Runnable callback) {
        if (callback == null) return;
        if (cancelled.get()) {
            try {
                callback.run();
            } catch (Exception ignored) {}
        } else {
            callbacks.add(callback);
        }
    }

    public void registerProcess(Process process) {
        if (process == null) return;
        if (cancelled.get()) {
            destroyProcessTree(process);
        } else {
            registeredProcesses.add(process);
        }
    }

    public void registerThread(Thread thread) {
        if (thread == null) return;
        if (cancelled.get()) {
            try {
                if (thread.isAlive()) {
                    thread.interrupt();
                }
            } catch (Exception ignored) {}
        } else {
            registeredThreads.add(thread);
        }
    }

    private void destroyProcessTree(Process p) {
        if (p == null || !p.isAlive()) {
            return;
        }
        try {
            p.toHandle().descendants().forEach(h -> {
                try {
                    h.destroyForcibly();
                } catch (Exception ignored) {}
            });
            p.destroyForcibly();
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                try {
                    long pid = p.pid();
                    new ProcessBuilder("taskkill", "/PID", String.valueOf(pid), "/T", "/F")
                            .start().waitFor(1, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception ignored) {}
            }
            log.debug("Forcibly destroyed process tree for cancelled run [{}]", runId);
        } catch (Exception e) {
            log.warn("Error destroying process tree for run [{}]: {}", runId, e.getMessage());
        }
    }
}
