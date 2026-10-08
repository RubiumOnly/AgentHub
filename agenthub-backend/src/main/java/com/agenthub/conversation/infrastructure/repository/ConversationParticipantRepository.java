package com.agenthub.conversation.infrastructure.repository;

import com.agenthub.conversation.infrastructure.entity.ConversationParticipantEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipantEntity, String> {
    List<ConversationParticipantEntity> findByConversationId(String conversationId);
    void deleteByConversationId(String conversationId);
}
