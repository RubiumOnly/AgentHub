package com.agenthub.conversation.domain.service;

import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Message timeline sequencer guaranteeing strictly monotonic, gapless, conflict-free
 * sequence numbers for conversation messages across concurrent operations.
 * Employs striped fair reentrant locks intra-JVM and pessimistic DB row locks inter-instance.
 */
@Component
public class ConversationSequenceManager {

    private static final int STRIPE_COUNT = 128;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReentrantLock[] stripeLocks = new ReentrantLock[STRIPE_COUNT];

    public ConversationSequenceManager(ConversationRepository conversationRepository,
                                       MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripeLocks[i] = new ReentrantLock(true);
        }
    }

    private ReentrantLock getLock(String conversationId) {
        int index = Math.abs(conversationId.hashCode() % STRIPE_COUNT);
        return stripeLocks[index];
    }

    /**
     * Atomically allocates the next strictly monotonic sequence number for the specified conversation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long nextSequenceNum(String conversationId) {
        ReentrantLock lock = getLock(conversationId);
        lock.lock();
        try {
            ConversationEntity conv = conversationRepository.findByIdForUpdate(conversationId)
                    .orElseGet(() -> conversationRepository.findById(conversationId)
                            .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId)));

            long currentSeq = conv.getLastSequenceNum() != null ? conv.getLastSequenceNum() : 0L;
            if (currentSeq == 0L) {
                long messageCount = messageRepository.countByConversationId(conversationId);
                if (messageCount > 0) {
                    currentSeq = messageCount;
                }
            }

            long nextSeq = currentSeq + 1L;
            conv.setLastSequenceNum(nextSeq);
            conversationRepository.saveAndFlush(conv);
            return nextSeq;
        } finally {
            lock.unlock();
        }
    }
}
