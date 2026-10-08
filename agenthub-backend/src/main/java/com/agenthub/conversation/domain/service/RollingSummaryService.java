package com.agenthub.conversation.domain.service;

import com.agenthub.conversation.dto.MessageView;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Summarizes pruned older conversation turns into a rolling historical synopsis,
 * preventing Context Overflow while retaining critical technical decisions and goals.
 */
@Component
public class RollingSummaryService {

    /**
     * Synthesizes an updated rolling summary incorporating newly aged-out messages.
     *
     * @param existingSummary The current summary before pruning
     * @param agedOutMessages Messages that fell outside the active sliding window
     * @return Compacted summary text suitable for prepending to LLM prompts
     */
    public String synthesizeRollingSummary(String existingSummary, List<MessageView> agedOutMessages) {
        if (agedOutMessages == null || agedOutMessages.isEmpty()) {
            return existingSummary != null ? existingSummary : "";
        }

        StringBuilder sb = new StringBuilder();
        if (existingSummary != null && !existingSummary.isBlank()) {
            sb.append(existingSummary.trim()).append("\n");
        } else {
            sb.append("【前序会话滚动历史摘要】\n");
        }

        sb.append("- [近期历史归档，共计 ").append(agedOutMessages.size()).append(" 轮]：\n");

        for (MessageView msg : agedOutMessages) {
            String sender = msg.getSenderId() != null ? msg.getSenderId() : "User";
            String content = msg.getContent() != null ? msg.getContent().trim() : "";

            // Truncate long content to concise keypoint
            String brief;
            if (content.length() > 100) {
                brief = content.substring(0, 100).replaceAll("\\r?\\n", " ") + "...";
            } else {
                brief = content.replaceAll("\\r?\\n", " ");
            }

            if (!brief.isBlank()) {
                sb.append("  * ").append(sender).append(": ").append(brief).append("\n");
            }
        }

        return sb.toString().trim();
    }
}
