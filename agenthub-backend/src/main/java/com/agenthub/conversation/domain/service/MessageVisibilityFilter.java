package com.agenthub.conversation.domain.service;

import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.dto.MessageView;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforces message routing visibility and isolation between broadcast, direct P2P, and system events.
 */
@Component
public class MessageVisibilityFilter {

    /**
     * Determines whether a message is visible to a given viewer.
     */
    public boolean isVisible(MessageView message, String viewerId, boolean isPrivileged) {
        if (message == null) {
            return false;
        }

        // Privileged viewers (admin, system, orchestrator) see everything
        if (isPrivileged || "admin".equalsIgnoreCase(viewerId) || "system".equalsIgnoreCase(viewerId)) {
            return true;
        }

        // Broadcast and System events are visible to all members
        MessageType type = message.getMessageType();
        if (type == null || type == MessageType.BROADCAST || type == MessageType.SYSTEM) {
            return true;
        }

        // Direct P2P message: only sender and recipient can see
        if (type == MessageType.DIRECT) {
            if (viewerId == null || viewerId.isBlank()) {
                return false;
            }
            return viewerId.equalsIgnoreCase(message.getSenderId())
                    || (message.getRecipientId() != null && viewerId.equalsIgnoreCase(message.getRecipientId()));
        }

        return true;
    }

    /**
     * Filters a list of messages keeping only those visible to the viewer.
     */
    public List<MessageView> filterVisible(List<MessageView> messages, String viewerId, boolean isPrivileged) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }
        if (viewerId == null || viewerId.isBlank() || isPrivileged || "admin".equalsIgnoreCase(viewerId)) {
            return messages;
        }

        return messages.stream()
                .filter(m -> isVisible(m, viewerId, isPrivileged))
                .collect(Collectors.toList());
    }
}
