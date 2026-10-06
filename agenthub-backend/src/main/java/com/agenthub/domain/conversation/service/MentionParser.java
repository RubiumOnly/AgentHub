package com.agenthub.domain.conversation.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class MentionParser {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([a-zA-Z0-9_\\-\\u4e00-\\u9fa5]+)");

    public List<String> extractMentions(String messageContent) {
        if (messageContent == null || messageContent.isBlank()) {
            return List.of();
        }
        Set<String> mentions = new LinkedHashSet<>();
        Matcher matcher = MENTION_PATTERN.matcher(messageContent);
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return new ArrayList<>(mentions);
    }

    public boolean hasOrchestratorMention(String messageContent) {
        List<String> mentions = extractMentions(messageContent);
        for (String m : mentions) {
            if ("orchestrator".equalsIgnoreCase(m) || "协调器".equalsIgnoreCase(m) || "编排器".equalsIgnoreCase(m)) {
                return true;
            }
        }
        return false;
    }
}
