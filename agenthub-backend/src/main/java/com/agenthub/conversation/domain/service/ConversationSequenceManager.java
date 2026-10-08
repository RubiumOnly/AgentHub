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
 */
@Component
public class ConversationSequenceManager {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ConversationSequenceManager(ConversationRepository conversationRepository,
                                       MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    /**
     * Atomically allocates the next strictly monotonic sequence number for the specified conversation.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long nextSequenceNum(String conversationId) {
        ReentrantLock lock = locks.computeIfAbsent(conversationId, k -> new ReentrantLock());
        lock.lock();
        try {
            ConversationEntity conv = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId));

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
