package com.agenthub.conversation.domain.service;

import com.agenthub.infrastructure.repository.ConversationRepository;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.shared.exception.BusinessException;
import com.agenthub.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate transactionTemplate;
    private final ReentrantLock[] stripeLocks = new ReentrantLock[STRIPE_COUNT];

    public ConversationSequenceManager(ConversationRepository conversationRepository,
                                       MessageRepository messageRepository,
                                       PlatformTransactionManager transactionManager) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripeLocks[i] = new ReentrantLock(true);
        }
    }

    private ReentrantLock getLock(String conversationId) {
        int hash = conversationId != null ? conversationId.hashCode() : 0;
        int index = (hash & 0x7fffffff) % STRIPE_COUNT;
        return stripeLocks[index];
    }

    /**
     * Atomically allocates the next strictly monotonic sequence number for the specified conversation.
     * Guarantees physical database commit before releasing intra-JVM lock.
     */
    public long nextSequenceNum(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation ID cannot be null or empty");
        }
        ReentrantLock lock = getLock(conversationId);
        lock.lock();
        try {
            Long nextSeq = transactionTemplate.execute(status -> {
                ConversationEntity conv = conversationRepository.findByIdForUpdate(conversationId)
                        .orElseGet(() -> conversationRepository.findById(conversationId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation not found: " + conversationId)));

                long currentSeq = conv.getLastSequenceNum() != null ? conv.getLastSequenceNum() : 0L;
                if (currentSeq == 0L) {
                    long maxMsgSeq = messageRepository.findTopByConversationIdOrderBySequenceNumDesc(conversationId)
                            .map(com.agenthub.infrastructure.repository.entity.MessageEntity::getSequenceNum)
                            .orElse(0L);
                    if (maxMsgSeq > 0) {
                        currentSeq = maxMsgSeq;
                    } else {
                        long messageCount = messageRepository.countByConversationId(conversationId);
                        if (messageCount > 0) {
                            currentSeq = messageCount;
                        }
                    }
                }

                long allocated = currentSeq + 1L;
                conv.setLastSequenceNum(allocated);
                conversationRepository.saveAndFlush(conv);
                return allocated;
            });
            return nextSeq != null ? nextSeq : 1L;
        } finally {
            lock.unlock();
        }
    }
}
