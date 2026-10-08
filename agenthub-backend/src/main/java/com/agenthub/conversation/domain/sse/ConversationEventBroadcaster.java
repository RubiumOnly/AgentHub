package com.agenthub.conversation.domain.sse;

import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.domain.service.MessageVisibilityFilter;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages real-time SSE event subscriptions for conversations, including Last-Event-ID
 * sequence replay upon reconnection, periodic keep-alive heartbeats, and viewer visibility filtering.
 */
@Component
public class ConversationEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(ConversationEventBroadcaster.class);

    private final MessageRepository messageRepository;
    private final MessageVisibilityFilter visibilityFilter;

    // conversationId -> list of active EmitterHolders
    private final Map<String, List<EmitterHolder>> emitterMap = new ConcurrentHashMap<>();

    public static class EmitterHolder {
        private final SseEmitter emitter;
        private final String viewerId;

        public EmitterHolder(SseEmitter emitter, String viewerId) {
            this.emitter = emitter;
            this.viewerId = viewerId;
        }

        public SseEmitter getEmitter() { return emitter; }
        public String getViewerId() { return viewerId; }
    }

    public ConversationEventBroadcaster(MessageRepository messageRepository,
                                        MessageVisibilityFilter visibilityFilter) {
        this.messageRepository = messageRepository;
        this.visibilityFilter = visibilityFilter;
    }

    /**
     * Registers a new SSE stream with replay of historical events if Last-Event-ID is provided.
     */
    public SseEmitter registerStream(String conversationId, String lastEventId, String viewerId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        EmitterHolder holder = new EmitterHolder(emitter, viewerId);

        emitterMap.computeIfAbsent(conversationId, k -> new CopyOnWriteArrayList<>()).add(holder);

        emitter.onCompletion(() -> removeHolder(conversationId, holder));
        emitter.onTimeout(() -> removeHolder(conversationId, holder));
        emitter.onError((e) -> removeHolder(conversationId, holder));

        try {
            // Initial connected handshake
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("conversationId", conversationId, "status", "ready")));

            // Replay historical messages if client provided Last-Event-ID or sinceSeq
            if (lastEventId != null && !lastEventId.isBlank()) {
                try {
                    long sinceSeq = Long.parseLong(lastEventId.trim());
                    List<MessageEntity> missed = messageRepository
                            .findByConversationIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(conversationId, sinceSeq);

                    for (MessageEntity entity : missed) {
                        MessageView view = toMessageView(entity);
                        if (visibilityFilter.isVisible(view, viewerId, false)) {
                            emitter.send(SseEmitter.event()
                                    .id(String.valueOf(view.getSequenceNum()))
                                    .name("message")
                                    .data(view));
                        }
                    }
                } catch (NumberFormatException nfe) {
                    log.warn("Invalid Last-Event-ID format: {}", lastEventId);
                }
            }
        } catch (IOException e) {
            removeHolder(conversationId, holder);
        }

        return emitter;
    }

    /**
     * Broadcasts an event to all subscribers of a conversation, honoring visibility filters for DIRECT messages.
     */
    public void broadcastEvent(String conversationId, String eventName, Object data) {
        List<EmitterHolder> holders = emitterMap.get(conversationId);
        if (holders == null || holders.isEmpty()) return;

        List<EmitterHolder> deadHolders = new ArrayList<>();
        for (EmitterHolder holder : holders) {
            try {
                // If data is a MessageView, verify visibility before sending
                if (data instanceof MessageView mv) {
                    if (!visibilityFilter.isVisible(mv, holder.getViewerId(), false)) {
                        continue;
                    }
                    holder.getEmitter().send(SseEmitter.event()
                            .id(mv.getSequenceNum() != null ? String.valueOf(mv.getSequenceNum()) : null)
                            .name(eventName)
                            .data(data));
                } else {
                    holder.getEmitter().send(SseEmitter.event()
                            .name(eventName)
                            .data(data));
                }
            } catch (Exception e) {
                deadHolders.add(holder);
            }
        }

        if (!deadHolders.isEmpty()) {
            holders.removeAll(deadHolders);
            if (holders.isEmpty()) {
                emitterMap.remove(conversationId);
            }
        }
    }

    /**
     * Periodic keep-alive heartbeat to maintain long-lived SSE connections and detect dead clients.
     */
    @Scheduled(fixedRate = 15000)
    public void sendHeartbeat() {
        for (Map.Entry<String, List<EmitterHolder>> entry : emitterMap.entrySet()) {
            List<EmitterHolder> holders = entry.getValue();
            List<EmitterHolder> dead = new ArrayList<>();
            for (EmitterHolder holder : holders) {
                try {
                    holder.getEmitter().send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of("timestamp", System.currentTimeMillis())));
                } catch (Exception e) {
                    dead.add(holder);
                }
            }
            holders.removeAll(dead);
            if (holders.isEmpty()) {
                emitterMap.remove(entry.getKey());
            }
        }
    }

    public int getActiveSubscriberCount(String conversationId) {
        List<EmitterHolder> list = emitterMap.get(conversationId);
        return list != null ? list.size() : 0;
    }

    private void removeHolder(String conversationId, EmitterHolder holder) {
        List<EmitterHolder> list = emitterMap.get(conversationId);
        if (list != null) {
            list.remove(holder);
            if (list.isEmpty()) {
                emitterMap.remove(conversationId);
            }
        }
    }

    private MessageView toMessageView(MessageEntity entity) {
        return new MessageView(
                entity.getId(),
                entity.getConversationId(),
                entity.getSenderId(),
                entity.getSenderType(),
                entity.getRecipientId(),
                entity.getMessageType(),
                entity.getProtocolType(),
                entity.getContent(),
                entity.getCardPayloadJson(),
                entity.getMentions(),
                entity.getSchemaVersion(),
                entity.getSequenceNum(),
                entity.getTokenCount(),
                entity.getInReplyToId(),
                entity.getCreatedAt()
        );
    }
}
