package com.agenthub.execution.service;

import com.agenthub.execution.infrastructure.entity.RunEventEntity;
import com.agenthub.execution.infrastructure.repository.RunEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Real-time SSE event broadcaster with monotonic sequence tracking,
 * Last-Event-ID reconnection replay, and automated heartbeat.
 */
@Component
public class RunEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(RunEventBroadcaster.class);
    private static final long SSE_TIMEOUT_MS = 180_000L; // 3 minutes timeout
    private static final int MAX_REPLAY_LIMIT = 2000; // Cap replay query to protect memory

    private final RunEventRepository runEventRepository;
    private final Map<String, List<SseEmitter>> activeEmitters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> runSequenceCounters = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.agenthub.infrastructure.metrics.AgentHubMetricsCollector metricsCollector;

    public RunEventBroadcaster(RunEventRepository runEventRepository) {
        this.runEventRepository = runEventRepository;
    }

    private AtomicLong getSequenceCounter(String runId) {
        return runSequenceCounters.computeIfAbsent(runId, k -> {
            Long maxSeq = runEventRepository.findMaxSequenceNum(runId);
            return new AtomicLong(maxSeq != null ? maxSeq : 0L);
        });
    }

    /**
     * Subscribe to real-time execution events for a Run.
     * If lastEventId is provided, immediately replays all historical events missed after that cursor (bounded by MAX_REPLAY_LIMIT).
     */
    public SseEmitter subscribe(String runId, Long lastEventId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        activeEmitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        if (metricsCollector != null) {
            metricsCollector.incrementActiveSseConnections();
        }

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError((e) -> removeEmitter(runId, emitter));

        // 1. Replay missed events if Last-Event-ID cursor provided (bounded by PageRequest)
        int replayedCount = 0;
        if (lastEventId != null && lastEventId >= 0) {
            org.springframework.data.domain.Pageable limit = org.springframework.data.domain.PageRequest.of(0, MAX_REPLAY_LIMIT);
            List<RunEventEntity> missedEvents;
            if (lastEventId == 0) {
                missedEvents = runEventRepository.findByRunIdOrderBySequenceNumAsc(runId, limit);
            } else {
                missedEvents = runEventRepository.findByRunIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(runId, lastEventId, limit);
            }

            for (RunEventEntity event : missedEvents) {
                try {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(event.getSequenceNum()))
                            .name(event.getEventType())
                            .data(event.getPayload()));
                    replayedCount++;
                } catch (IOException e) {
                    log.debug("Client disconnected during historical event replay for run [{}]: {}", runId, e.getMessage());
                    removeEmitter(runId, emitter);
                    return emitter;
                }
            }
        }

        // 2. Send connected handshake event
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "runId", runId,
                            "replayedCount", replayedCount,
                            "status", "STREAM_OPENED"
                    )));
        } catch (IOException ignored) {}

        return emitter;
    }

    /**
     * Atomically persist event to database with monotonic sequenceId and broadcast via SSE.
     */
    @Transactional
    public RunEventEntity publishEvent(String runId, String eventType, String payload) {
        long seq = getSequenceCounter(runId).incrementAndGet();
        String eventId = "evt-" + UUID.randomUUID().toString().substring(0, 8);
        RunEventEntity event = new RunEventEntity(eventId, runId, seq, eventType, payload);
        RunEventEntity saved = runEventRepository.save(event);

        broadcastLive(runId, saved);
        return saved;
    }

    private void broadcastLive(String runId, RunEventEntity event) {
        List<SseEmitter> emitters = activeEmitters.get(runId);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(event.getSequenceNum()))
                        .name(event.getEventType())
                        .data(event.getPayload()));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }

    private void removeEmitter(String runId, SseEmitter emitter) {
        List<SseEmitter> list = activeEmitters.get(runId);
        if (list != null) {
            boolean removed = list.remove(emitter);
            if (removed && metricsCollector != null) {
                metricsCollector.decrementActiveSseConnections();
            }
            if (list.isEmpty()) {
                activeEmitters.remove(runId);
            }
        }
    }

    /**
     * Heartbeat ping to keep active connections alive across HTTP proxies/gateways.
     */
    @Scheduled(fixedRate = 20_000)
    public void sendHeartbeats() {
        for (Map.Entry<String, List<SseEmitter>> entry : activeEmitters.entrySet()) {
            String runId = entry.getKey();
            List<SseEmitter> emitters = entry.getValue();
            List<SseEmitter> deadEmitters = new ArrayList<>();

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat:" + System.currentTimeMillis()));
                } catch (Exception e) {
                    deadEmitters.add(emitter);
                }
            }
            emitters.removeAll(deadEmitters);
        }
    }

    public int getActiveSubscriberCount(String runId) {
        List<SseEmitter> list = activeEmitters.get(runId);
        return list != null ? list.size() : 0;
    }

    /**
     * Evict memory state for completed or cancelled run.
     */
    public void evictRun(String runId) {
        if (runId != null) {
            runSequenceCounters.remove(runId);
            List<SseEmitter> emitters = activeEmitters.remove(runId);
            if (emitters != null) {
                for (SseEmitter emitter : emitters) {
                    try {
                        emitter.complete();
                    } catch (Exception ignored) {}
                }
            }
        }
    }
}
