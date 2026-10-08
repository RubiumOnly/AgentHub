package com.agenthub.conversation.domain.service;

import com.agenthub.conversation.dto.MessageView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits conversation history into an active sliding window and archived older messages.
 */
@Component
public class SlidingWindowContextTrimmer {

    public static class WindowSplit {
        private final List<MessageView> olderMessages;
        private final List<MessageView> activeMessages;

        public WindowSplit(List<MessageView> olderMessages, List<MessageView> activeMessages) {
            this.olderMessages = olderMessages != null ? olderMessages : Collections.emptyList();
            this.activeMessages = activeMessages != null ? activeMessages : Collections.emptyList();
        }

        public List<MessageView> getOlderMessages() { return olderMessages; }
        public List<MessageView> getActiveMessages() { return activeMessages; }
        public int getTrimmedCount() { return olderMessages.size(); }
    }

    /**
     * Splits messages into older messages (to be summarized) and active messages (kept in prompt).
     */
    public WindowSplit trimToWindow(List<MessageView> messages, int windowSize) {
        if (messages == null || messages.isEmpty()) {
            return new WindowSplit(Collections.emptyList(), Collections.emptyList());
        }
        if (windowSize <= 0 || messages.size() <= windowSize) {
            return new WindowSplit(Collections.emptyList(), new ArrayList<>(messages));
        }

        int splitPoint = messages.size() - windowSize;
        List<MessageView> older = new ArrayList<>(messages.subList(0, splitPoint));
        List<MessageView> active = new ArrayList<>(messages.subList(splitPoint, messages.size()));

        return new WindowSplit(older, active);
    }
}
