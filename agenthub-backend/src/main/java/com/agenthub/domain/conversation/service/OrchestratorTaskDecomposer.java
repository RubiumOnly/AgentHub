package com.agenthub.domain.conversation.service;

import com.agenthub.domain.conversation.model.InteractiveCard;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class OrchestratorTaskDecomposer {

    public InteractiveCard decomposeTask(String taskPrompt) {
        String cardId = "card-" + UUID.randomUUID().toString().substring(0, 8);
        InteractiveCard card = new InteractiveCard(
                cardId,
                "🤖 Orchestrator 多 Agent 任务拆解计划",
                "已根据用户需求分析，自动完成 3 阶段子任务拓扑拆解并派发至群聊。"
        );

        String summary = taskPrompt != null ? taskPrompt.trim() : "系统特性开发";
        String uuidSuffix = UUID.randomUUID().toString().substring(0, 4);

        // Phase 1: Architecture & Backend
        card.addSubtask(
                "sub-1-" + uuidSuffix,
                "BackendArchitect",
                "【阶段一】数据结构、RESTful API 契约与 DDD 领域模型设计: " + summary,
                "COMPLETED"
        );

        // Phase 2: Frontend
        card.addSubtask(
                "sub-2-" + uuidSuffix,
                "FrontendEngineer",
                "【阶段二】Next.js 14 响应式交互界面与组件实现",
                "RUNNING"
        );

        // Phase 3: Verification & QA
        card.addSubtask(
                "sub-3-" + uuidSuffix,
                "QAAuditor",
                "【阶段三】JGit 代码 Diff 审查与全链路集成测试",
                "PENDING"
        );

        card.getActions().add("一键审批执行");
        card.getActions().add("重试阶段");
        return card;
    }
}
