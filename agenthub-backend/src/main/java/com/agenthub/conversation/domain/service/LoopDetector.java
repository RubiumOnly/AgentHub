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

        // 1. Max turns threshold check for autonomous collaboration
        // Count consecutive agent/autonomous turns since the latest USER intervention
        int consecutiveAgentTurns = 0;
        Set<String> uniqueSenders = new LinkedHashSet<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            MessageView m = messages.get(i);
            if (m.getSenderType() == com.agenthub.domain.conversation.model.SenderType.USER) {
                break;
            }
            consecutiveAgentTurns++;
            if (m.getSenderId() != null) {
                uniqueSenders.add(m.getSenderId());
            }
        }

        // If all messages in history are from agents or consecutive agent turns >= maxTurns
        if (maxTurns > 0 && consecutiveAgentTurns >= maxTurns) {
            return LoopDetectionResult.maxTurnsExceeded(maxTurns, new ArrayList<>(uniqueSenders));
        }

        // 2. Duplicate content collision detection (agents only)
        MessageView latest = messages.get(messages.size() - 1);
        if (isAgent(latest) && latest.getContent() != null && !latest.getContent().isBlank()) {
            String normLatest = normalizeContent(latest.getContent());
            int window = Math.min(messages.size() - 1, 6);
            for (int i = messages.size() - 2; i >= messages.size() - 1 - window; i--) {
                MessageView prev = messages.get(i);
                if (isAgent(prev) && latest.getSenderId().equalsIgnoreCase(prev.getSenderId()) && prev.getContent() != null) {
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

        // 3. Multi-agent oscillation & circular ping-pong detection
        int n = messages.size();

        // 3a. Self-loop repetition: same agent repeats 3 consecutive turns without protocol break
        if (n >= 3) {
            MessageView s1 = messages.get(n - 3);
            MessageView s2 = messages.get(n - 2);
            MessageView s3 = messages.get(n - 1);
            if (isAgent(s1) && isAgent(s2) && isAgent(s3) &&
                s1.getSenderId() != null &&
                s1.getSenderId().equalsIgnoreCase(s2.getSenderId()) &&
                s1.getSenderId().equalsIgnoreCase(s3.getSenderId())) {
                if (!isProtocolBreak(s1) && !isProtocolBreak(s2) && !isProtocolBreak(s3)) {
                    return LoopDetectionResult.oscillation(
                            List.of(s1.getSenderId()),
                            "Identified self-loop repetition across 3 consecutive turns by agent: " + s1.getSenderId()
                    );
                }
            }
        }

        // 3b. 2-party alternating oscillation: [A, B, A, B] without task progression
        if (n >= 4) {
            MessageView m1 = messages.get(n - 4);
            MessageView m2 = messages.get(n - 3);
            MessageView m3 = messages.get(n - 2);
            MessageView m4 = messages.get(n - 1);

            if (isAgent(m1) && isAgent(m2) && isAgent(m3) && isAgent(m4)) {
                String a1 = m1.getSenderId();
                String b1 = m2.getSenderId();
                String a2 = m3.getSenderId();
                String b2 = m4.getSenderId();

                if (a1 != null && b1 != null && !a1.equalsIgnoreCase(b1)) {
                    if (a1.equalsIgnoreCase(a2) && b1.equalsIgnoreCase(b2)) {
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
        }

        // 3c. 3-party circular oscillation: [A, B, C, A, B, C] without task progression
        if (n >= 6) {
            MessageView m1 = messages.get(n - 6);
            MessageView m2 = messages.get(n - 5);
            MessageView m3 = messages.get(n - 4);
            MessageView m4 = messages.get(n - 3);
            MessageView m5 = messages.get(n - 2);
            MessageView m6 = messages.get(n - 1);

            if (isAgent(m1) && isAgent(m2) && isAgent(m3) && isAgent(m4) && isAgent(m5) && isAgent(m6)) {
                String a1 = m1.getSenderId();
                String b1 = m2.getSenderId();
                String c1 = m3.getSenderId();
                String a2 = m4.getSenderId();
                String b2 = m5.getSenderId();
                String c2 = m6.getSenderId();

                if (a1 != null && b1 != null && c1 != null &&
                    !a1.equalsIgnoreCase(b1) && !b1.equalsIgnoreCase(c1) && !a1.equalsIgnoreCase(c1)) {
                    if (a1.equalsIgnoreCase(a2) && b1.equalsIgnoreCase(b2) && c1.equalsIgnoreCase(c2)) {
                        boolean hasProtocolBreak = isProtocolBreak(m1) || isProtocolBreak(m2) || isProtocolBreak(m3) ||
                                                   isProtocolBreak(m4) || isProtocolBreak(m5) || isProtocolBreak(m6);
                        if (!hasProtocolBreak) {
                            return LoopDetectionResult.oscillation(
                                    List.of(a1, b1, c1),
                                    "Identified circular 3-party oscillation sequence across 6 consecutive turns: [" +
                                            a1 + " -> " + b1 + " -> " + c1 + " -> " + a2 + " -> " + b2 + " -> " + c2 + "]."
                            );
                        }
                    }
                }
            }
        }

        return LoopDetectionResult.clean();
    }

    private boolean isAgent(MessageView m) {
        if (m == null) return false;
        return m.getSenderType() == com.agenthub.domain.conversation.model.SenderType.AGENT ||
               m.getSenderType() == com.agenthub.domain.conversation.model.SenderType.ORCHESTRATOR;
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
