package com.agenthub.conversation.application.pipeline;

import com.agenthub.conversation.domain.model.MessageProtocolType;
import com.agenthub.conversation.domain.model.MessageType;
import com.agenthub.domain.conversation.model.SenderType;
import com.agenthub.conversation.domain.service.ConversationSequenceManager;
import com.agenthub.conversation.domain.service.TokenBudgetContextManager;
import com.agenthub.conversation.domain.sse.ConversationEventBroadcaster;
import com.agenthub.conversation.dto.MessageView;
import com.agenthub.conversation.infrastructure.repository.ConversationParticipantRepository;
import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;
import com.agenthub.domain.agent.service.AgentAdapterFactory;
import com.agenthub.domain.agent.spi.UnifiedAgentAdapter;
import com.agenthub.domain.conversation.model.ConversationType;
import com.agenthub.domain.conversation.model.InteractiveCard;
import com.agenthub.domain.conversation.service.MentionParser;
import com.agenthub.domain.conversation.service.OrchestratorTaskDecomposer;
import com.agenthub.infrastructure.repository.MessageRepository;
import com.agenthub.infrastructure.repository.entity.ConversationEntity;
import com.agenthub.infrastructure.repository.entity.MessageEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * 旧版 @Mention 代理调用管线（Phase 10 R7 自 ConversationApplicationService 抽出）：
 * Orchestrator 拆解卡片与按提及逐 Agent 执行；仅服务非团队协作会话。
 */
@Component
public class LegacyMentionPipeline {

    private final MentionParser mentionParser;
    private final OrchestratorTaskDecomposer orchestratorTaskDecomposer;
    private final AgentAdapterFactory agentAdapterFactory;
    private final ConversationSequenceManager sequenceManager;
    private final MessageRepository messageRepository;
    private final TokenBudgetContextManager tokenBudgetManager;
    private final ConversationEventBroadcaster eventBroadcaster;
    private final ConversationParticipantRepository participantRepository;

    @Value("${agenthub.workspace.base-dir:./data/workspaces}")
    private String workspaceBaseDir;

    public LegacyMentionPipeline(MentionParser mentionParser,
                                 OrchestratorTaskDecomposer orchestratorTaskDecomposer,
                                 AgentAdapterFactory agentAdapterFactory,
                                 ConversationSequenceManager sequenceManager,
                                 MessageRepository messageRepository,
                                 TokenBudgetContextManager tokenBudgetManager,
                                 ConversationEventBroadcaster eventBroadcaster,
                                 ConversationParticipantRepository participantRepository) {
        this.mentionParser = mentionParser;
        this.orchestratorTaskDecomposer = orchestratorTaskDecomposer;
        this.agentAdapterFactory = agentAdapterFactory;
        this.sequenceManager = sequenceManager;
        this.messageRepository = messageRepository;
        this.tokenBudgetManager = tokenBudgetManager;
        this.eventBroadcaster = eventBroadcaster;
        this.participantRepository = participantRepository;
    }

    public void handleLegacyMentions(ConversationEntity conversation, String content, List<String> mentions,
                                     SenderType senderType, MessageType messageType,
                                     Function<MessageEntity, MessageView> viewMapper) {
        String convId = conversation.getId();
        String workspaceDir = workspaceBaseDir + "/default";

        boolean isOrchestratorTargeted = mentionParser.hasOrchestratorMention(content) ||
                (senderType == SenderType.USER && messageType == MessageType.BROADCAST &&
                 conversation.getType() == ConversationType.GROUP_COLLABORATION && mentions.isEmpty());

        if (isOrchestratorTargeted) {
            InteractiveCard card = orchestratorTaskDecomposer.decomposeTask(content);
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String cardJson = objectMapper.writeValueAsString(card);
                long cardSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity cardMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "Orchestrator",
                        SenderType.ORCHESTRATOR,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "已完成多 Agent 协作任务拆解与编排派发。",
                        "v1",
                        cardSeq,
                        tokenBudgetManager.estimateTokens(cardJson),
                        null
                );
                cardMsg.setCardPayloadJson(cardJson);
                messageRepository.save(cardMsg);
                eventBroadcaster.broadcastEvent(convId, "message", viewMapper.apply(cardMsg));

                UnifiedAgentAdapter apiAdapter = agentAdapterFactory.getAdapter(AgentPlatformType.SPRING_AI_API);
                AgentExecutionRequest backendReq = new AgentExecutionRequest(
                        "BackendArchitect",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深后端架构师，请针对业务需求「" + content + "」编写完整的 Spring Boot 3 控制器或服务层核心代码。"
                );
                AgentExecutionResult backendRes = apiAdapter.execute(backendReq);
                long backendSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity backendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "BackendArchitect",
                        SenderType.AGENT,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "【阶段一完成】后端领域服务代码已编写并写入工作区：\n" + backendRes.getOutput(),
                        "v1",
                        backendSeq,
                        tokenBudgetManager.estimateTokens(backendRes.getOutput()),
                        null
                );
                messageRepository.save(backendMsg);
                eventBroadcaster.broadcastEvent(convId, "message", viewMapper.apply(backendMsg));

                AgentExecutionRequest frontendReq = new AgentExecutionRequest(
                        "FrontendEngineer",
                        AgentPlatformType.SPRING_AI_API,
                        workspaceDir,
                        "你作为资深前端工程师，请针对业务需求「" + content + "」编写完整的 Next.js 14 / React 响应式界面组件代码。"
                );
                AgentExecutionResult frontendRes = apiAdapter.execute(frontendReq);
                long frontendSeq = sequenceManager.nextSequenceNum(convId);
                MessageEntity frontendMsg = new MessageEntity(
                        "msg-" + UUID.randomUUID().toString().substring(0, 8),
                        convId,
                        "FrontendEngineer",
                        SenderType.AGENT,
                        null,
                        MessageType.BROADCAST,
                        MessageProtocolType.NORMAL,
                        "【阶段二完成】前端交互界面代码已编写并写入工作区：\n" + frontendRes.getOutput(),
                        "v1",
                        frontendSeq,
                        tokenBudgetManager.estimateTokens(frontendRes.getOutput()),
                        null
                );
                messageRepository.save(frontendMsg);
                eventBroadcaster.broadcastEvent(convId, "message", viewMapper.apply(frontendMsg));

            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(LegacyMentionPipeline.class)
                        .error("Failed to execute Orchestrator workflow: ", e);
            }
        }

        for (String mention : mentions) {
            if ("orchestrator".equalsIgnoreCase(mention)) continue;

            AgentPlatformType platformType = AgentPlatformType.fromCode(mention);
            UnifiedAgentAdapter adapter = agentAdapterFactory.getAdapter(platformType);

            AgentExecutionRequest request = new AgentExecutionRequest(
                    mention,
                    platformType,
                    workspaceDir,
                    content
            );

            adapter.executeStream(
                    request,
                    chunk -> eventBroadcaster.broadcastEvent(convId, "message_delta", Map.of("sender", mention, "delta", chunk)),
                    result -> {
                        long seq = sequenceManager.nextSequenceNum(convId);
                        MessageEntity agentMsg = new MessageEntity(
                                "msg-" + UUID.randomUUID().toString().substring(0, 8),
                                convId,
                                mention,
                                SenderType.AGENT,
                                null,
                                MessageType.BROADCAST,
                                MessageProtocolType.NORMAL,
                                result.getOutput(),
                                "v1",
                                seq,
                                tokenBudgetManager.estimateTokens(result.getOutput()),
                                null
                        );
                        messageRepository.save(agentMsg);
                        eventBroadcaster.broadcastEvent(convId, "message", viewMapper.apply(agentMsg));
                    }
            );
        }
    }
}
