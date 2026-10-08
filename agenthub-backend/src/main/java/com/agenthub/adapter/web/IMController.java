package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.conversation.application.ConversationApplication;
import com.agenthub.conversation.dto.ConversationView;
import com.agenthub.conversation.dto.CreateConversationCommand;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.dto.SendMessageCommand;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/im")
public class IMController {

    private final ConversationApplication conversationApplication;

    public IMController(ConversationApplication conversationApplication) {
        this.conversationApplication = conversationApplication;
    }

    public static class CreateConversationRequest {
        public String title;
        public ConversationType type;
        public List<String> agentIds;
        public String projectId;
    }

    public static class SendMessageRequest {
        public String senderId;
        public SenderType senderType;
        public String content;
    }

    @PostMapping("/conversations")
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
                req.projectId != null ? req.projectId : "proj-default"
        );
        ConversationView view = conversationApplication.createConversation(cmd);
        return Result.ok(view);
    }

    @GetMapping("/conversations")
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

    @GetMapping("/conversations/{id}")
    public Result<ConversationView> getConversation(@PathVariable("id") String id) {
        return Result.ok(conversationApplication.getConversationById(id));
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageView>> getMessages(@PathVariable("id") String id) {
        return Result.ok(conversationApplication.listMessages(id));
    }

    @PostMapping("/conversations/{id}/messages")
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
                req.content
        );
        MessageView message = conversationApplication.sendMessage(id, cmd);
        return Result.ok(message);
    }

    @GetMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") String id) {
        return conversationApplication.registerStream(id);
    }
}
