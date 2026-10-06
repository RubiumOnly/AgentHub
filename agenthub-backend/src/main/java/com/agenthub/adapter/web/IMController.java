package com.agenthub.adapter.web;

import com.agenthub.adapter.common.Result;
import com.agenthub.application.service.IMCollaborationService;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/im")
public class IMController {

    private final IMCollaborationService imService;

    public IMController(IMCollaborationService imService) {
        this.imService = imService;
    }

    public static class CreateConversationRequest {
        public String title;
        public ConversationType type;
        public List<String> agentIds;
    }

    public static class SendMessageRequest {
        public String senderId;
        public SenderType senderType;
        public String content;
    }

    @PostMapping("/conversations")
    public Result<ConversationEntity> createConversation(@RequestBody CreateConversationRequest req) {
        ConversationEntity entity = imService.createConversation(
                req.title,
                req.type != null ? req.type : ConversationType.DIRECT_CHAT,
                req.agentIds
        );
        return Result.ok(entity);
    }

    @GetMapping("/conversations")
    public Result<List<ConversationEntity>> listConversations() {
        return Result.ok(imService.listConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public Result<List<MessageEntity>> getMessages(@PathVariable("id") String id) {
        return Result.ok(imService.getMessages(id));
    }

    @PostMapping("/conversations/{id}/messages")
    public Result<MessageEntity> sendMessage(@PathVariable("id") String id, @RequestBody SendMessageRequest req) {
        MessageEntity message = imService.sendMessage(
                id,
                req.senderId != null ? req.senderId : "user-1",
                req.senderType != null ? req.senderType : SenderType.USER,
                req.content
        );
        return Result.ok(message);
    }

    @GetMapping(value = "/conversations/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable("id") String id) {
        return imService.registerStream(id);
    }
}
