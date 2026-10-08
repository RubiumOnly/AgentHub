package com.agenthub.infrastructure.repository;

import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    List<MessageEntity> findByConversationIdOrderBySequenceNumAsc(String conversationId);
    List<MessageEntity> findByConversationIdAndSequenceNumGreaterThanOrderBySequenceNumAsc(String conversationId, Long sequenceNum);
    Optional<MessageEntity> findTopByConversationIdOrderBySequenceNumDesc(String conversationId);
    long countByConversationId(String conversationId);
}
