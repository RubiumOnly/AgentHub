package com.agenthub.conversation.domain.service;

import com.agenthub.conversation.dto.MessageView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages token budget for model prompts, trimming oldest messages when token budget is exceeded.
 */
@Component
public class TokenBudgetContextManager {

    /**
     * Estimates token count for raw text based on bilingual heuristic (CJK and Latin text).
     */
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int cjkCount = 0;
        int nonCjkCount = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                cjkCount++;
            } else {
                nonCjkCount++;
            }
        }
        // Chinese: ~1.5 tokens per char; English/code: ~1 token per 4 chars
        int tokens = (int) Math.ceil(cjkCount * 1.5 + nonCjkCount * 0.25);
        return Math.max(1, tokens);
    }

    /**
     * Estimates tokens for a single message.
     */
    public int estimateMessageTokens(MessageView message) {
        if (message == null) return 0;
        int count = 4; // base message envelope tokens
        if (message.getContent() != null) {
            count += estimateTokens(message.getContent());
        }
        if (message.getCardPayloadJson() != null) {
            count += estimateTokens(message.getCardPayloadJson());
        }
        return count;
    }

    /**
     * Trims active messages to fit within the specified token budget alongside the rolling summary.
     */
    public List<MessageView> fitToBudget(List<MessageView> activeMessages, String rollingSummary, int maxTokenBudget) {
        if (activeMessages == null || activeMessages.isEmpty()) {
            return Collections.emptyList();
        }
        if (maxTokenBudget <= 0) {
            return new ArrayList<>(activeMessages);
        }

        int summaryTokens = estimateTokens(rollingSummary);
        int availableBudget = Math.max(100, maxTokenBudget - summaryTokens);

        // Calculate tokens from newest to oldest
        List<MessageView> reversed = new ArrayList<>(activeMessages);
        Collections.reverse(reversed);

        List<MessageView> selected = new ArrayList<>();
        int currentTokens = 0;

        for (MessageView msg : reversed) {
            int msgTokens = estimateMessageTokens(msg);
            if (currentTokens + msgTokens <= availableBudget || selected.isEmpty()) {
                selected.add(msg);
                currentTokens += msgTokens;
            } else {
                break;
            }
        }

        Collections.reverse(selected);
        return selected;
    }
}
