package com.agenthub.conversation.api;

import com.agenthub.adapter.common.Result;
import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.conversation.dto.*;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationApplication conversationApplication;

    public ConversationController(ConversationApplication conversationApplication) {
        this.conversationApplication = conversationApplication;
    }

    public static class CreateConversationRequest {
        public String title;
        public ConversationType type;
        public List<String> agentIds;
        public String projectId;
        public String teamId;
    }

    public static class SendMessageRequest {
        public String senderId;
        public SenderType senderType;
        public String recipientId;
        public MessageType messageType;
        public MessageProtocolType protocolType;
        public String content;
        public String inReplyToId;
    }

    @PostMapping
    public Result<ConversationView> createConversation(@RequestBody CreateConversationRequest req) {
        String currentUserId = com.agenthub.shared.context.RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new com.agenthub.shared.exception.BusinessException(
                    com.agenthub.shared.exception.ErrorCode.UNAUTHORIZED,
                    "Authentication required to create conversation"
            );
        }
        CreateConversationCommand cmd = new CreateConversationCommand(
                req.title,
                req.type != null ? req.type : ConversationType.DIRECT_CHAT,
                req.agentIds,
                req.projectId != null ? req.projectId : "proj-default",
                req.teamId
        );
        return Result.ok(conversationApplication.createConversation(cmd));
    }

    @GetMapping
    public Result<List<ConversationView>> listConversations() {
        String currentUserId = com.agenthub.shared.context.RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new com.agenthub.shared.exception.BusinessException(
                    com.agenthub.shared.exception.ErrorCode.UNAUTHORIZED,
                    "Authentication required to list conversations"
            );
        }
        return Result.ok(conversationApplication.listConversations());
    }

    @GetMapping("/{id}")
    public Result<ConversationView> getConversation(@PathVariable("id") String id) {
        return Result.ok(conversationApplication.getConversationById(id));
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageView>> getMessages(@PathVariable("id") String id,
                                                 @RequestParam(value = "sinceSeq", required = false) Long sinceSeq,
                                                 @RequestParam(value = "viewerId", required = false) String viewerId) {
        return Result.ok(conversationApplication.listMessages(id, sinceSeq, viewerId));
    }

    @PostMapping("/{id}/messages")
    public Result<MessageView> sendMessage(@PathVariable("id") String id, @RequestBody SendMessageRequest req) {
        String currentUserId = com.agenthub.shared.context.RequestContext.get().getUserId();
        if (currentUserId == null || currentUserId.isBlank()) {
            throw new com.agenthub.shared.exception.BusinessException(
                    com.agenthub.shared.exception.ErrorCode.UNAUTHORIZED,
                    "Authentication required to send message"
            );
        }
        SendMessageCommand cmd = new SendMessageCommand(
                req.senderId != null ? req.senderId : currentUserId,
                req.senderType != null ? req.senderType : SenderType.USER,
                req.recipientId,
                req.messageType,
                req.protocolType,
                req.content,
                req.inReplyToId
        );
        return Result.ok(conversationApplication.sendMessage(id, cmd));
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") String id,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(value = "sinceSeq", required = false) String sinceSeqParam,
                             @RequestParam(value = "viewerId", required = false) String viewerId) {
        String effectiveLastEventId = lastEventId != null ? lastEventId : sinceSeqParam;
        return conversationApplication.registerStream(id, effectiveLastEventId, viewerId);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable("id") String id,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId,
                             @RequestParam(value = "after", required = false) String afterSeqParam,
                             @RequestParam(value = "viewerId", required = false) String viewerId) {
        String effectiveLastEventId = lastEventId != null ? lastEventId : afterSeqParam;
        return conversationApplication.registerStream(id, effectiveLastEventId, viewerId);
    }

    @GetMapping("/{id}/context")
    public Result<ContextWindowView> getContextWindow(@PathVariable("id") String id,
                                                     @RequestParam(value = "windowSize", required = false, defaultValue = "8") int windowSize,
                                                     @RequestParam(value = "maxTokens", required = false, defaultValue = "4000") int maxTokens) {
        return Result.ok(conversationApplication.getContextWindow(id, windowSize, maxTokens));
    }

    @PostMapping("/{id}/summarize")
    public Result<ConversationSummaryView> summarize(@PathVariable("id") String id) {
        return Result.ok(conversationApplication.generateRollingSummary(id));
    }

    @PostMapping("/{id}/coordinate")
    public Result<Void> coordinate(@PathVariable("id") String id) {
        conversationApplication.triggerTeamTurn(id);
        return Result.ok(null);
    }
}
