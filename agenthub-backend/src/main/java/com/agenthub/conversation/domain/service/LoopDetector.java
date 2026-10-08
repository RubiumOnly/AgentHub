package com.agenthub.conversation.domain.service;

import com.agenthub.conversation.domain.model.LoopDetectionResult;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.dto.MessageView;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Detects infinite loops, duplicate collision cycles, and oscillating ping-pong patterns
 * in multi-agent conversations to protect against token exhaustion.
 */
@Component
public class LoopDetector {

    /**
     * Inspects conversation history and upcoming turn to detect potential or active deadlocks/loops.
     *
     * @param messages Recent messages in the conversation ordered by sequence
     * @param maxTurns Maximum turns allowed for the team/conversation
     * @return LoopDetectionResult indicating whether a loop is detected and details
     */
    public LoopDetectionResult detectLoop(List<MessageView> messages, int maxTurns) {
        if (messages == null || messages.isEmpty()) {
            return LoopDetectionResult.clean();
        }

        // 1. Max turns threshold check
        if (messages.size() >= maxTurns) {
            Set<String> uniqueSenders = new LinkedHashSet<>();
            for (MessageView m : messages) {
                if (m.getSenderId() != null) uniqueSenders.add(m.getSenderId());
            }
            return LoopDetectionResult.maxTurnsExceeded(maxTurns, new ArrayList<>(uniqueSenders));
        }

        // 2. Duplicate content collision detection
        // Check if latest message content closely collides with an earlier message from the same sender
        MessageView latest = messages.get(messages.size() - 1);
        if (latest.getContent() != null && !latest.getContent().isBlank()) {
            String normLatest = normalizeContent(latest.getContent());
            int window = Math.min(messages.size() - 1, 6);
            for (int i = messages.size() - 2; i >= messages.size() - 1 - window; i--) {
                MessageView prev = messages.get(i);
                if (latest.getSenderId().equalsIgnoreCase(prev.getSenderId()) && prev.getContent() != null) {
                    String normPrev = normalizeContent(prev.getContent());
                    if (normLatest.equals(normPrev) && normLatest.length() > 5) {
                        String snippet = latest.getContent().length() > 40
                                ? latest.getContent().substring(0, 40) + "..."
                                : latest.getContent();
                        return LoopDetectionResult.duplicateContent(latest.getSenderId(), snippet);
                    }
                }
            }
        }

        // 3. Short-cycle oscillation / ping-pong pattern detection
        // Look for pattern [A, B, A, B] without task progression (HANDOFF or SUMMARIZE)
        if (messages.size() >= 4) {
            int n = messages.size();
            MessageView m1 = messages.get(n - 4);
            MessageView m2 = messages.get(n - 3);
            MessageView m3 = messages.get(n - 2);
            MessageView m4 = messages.get(n - 1);

            String a1 = m1.getSenderId();
            String b1 = m2.getSenderId();
            String a2 = m3.getSenderId();
            String b2 = m4.getSenderId();

            if (a1 != null && b1 != null && !a1.equalsIgnoreCase(b1)) {
                if (a1.equalsIgnoreCase(a2) && b1.equalsIgnoreCase(b2)) {
                    // Check if any of these was a successful handoff or summarize
                    boolean hasProtocolBreak = isProtocolBreak(m1) || isProtocolBreak(m2) ||
                                               isProtocolBreak(m3) || isProtocolBreak(m4);
                    if (!hasProtocolBreak) {
                        return LoopDetectionResult.oscillation(
                                a1, b1,
                                "Identified strict alternating ping-pong sequence across 4 consecutive turns."
                        );
                    }
                }
            }
        }

        return LoopDetectionResult.clean();
    }

    private boolean isProtocolBreak(MessageView msg) {
        MessageProtocolType protocol = msg.getProtocolType();
        return protocol == MessageProtocolType.HANDOFF || protocol == MessageProtocolType.SUMMARIZE;
    }

    private String normalizeContent(String content) {
        if (content == null) return "";
        return content.toLowerCase().replaceAll("\\s+", "").trim();
    }
}
