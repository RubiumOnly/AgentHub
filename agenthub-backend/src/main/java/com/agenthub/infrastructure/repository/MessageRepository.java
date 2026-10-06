package com.agenthub.infrastructure.repository;

import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, String> {
    List<MessageEntity> findByConversationIdOrderByCreatedAtAsc(String conversationId);
}
