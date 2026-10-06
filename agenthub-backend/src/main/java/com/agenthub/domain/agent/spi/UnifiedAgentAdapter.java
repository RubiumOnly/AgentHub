package com.agenthub.domain.agent.spi;

import com.agenthub.domain.agent.model.AgentExecutionRequest;
import com.agenthub.domain.agent.model.AgentExecutionResult;
import com.agenthub.domain.agent.model.AgentPlatformType;

import java.util.function.Consumer;

public interface UnifiedAgentAdapter {

    /**
     * 获取支持的 Agent 平台类型
     */
    AgentPlatformType getSupportedPlatform();

    /**
     * 探测当前宿主机环境是否已安装或可用该 Agent 运行平台
     */
    boolean isAvailable();

    /**
     * 检查并返回平台版本标识
     */
    String checkVersion();

    /**
     * 同步/阻塞执行单次 Agent 交互任务
     */
    AgentExecutionResult execute(AgentExecutionRequest request);

    /**
     * 流式执行任务（输出增量 Token chunk，并在完成后触发 onComplete 回调）
     */
    void executeStream(AgentExecutionRequest request, Consumer<String> onChunk, Consumer<AgentExecutionResult> onComplete);
}
