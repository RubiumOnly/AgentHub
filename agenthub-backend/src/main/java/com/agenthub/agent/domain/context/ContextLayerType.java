package com.agenthub.agent.domain.context;

public enum ContextLayerType {
    SYSTEM_AND_PROJECT_RULES("系统角色与项目规约 (PROJECT_RULES.md)"),
    AOCI_COGNITION("AOCI 软件架构认知与代码索引基线"),
    AFFECTED_FILES("变更相关受控源码与文件上下文"),
    RUN_HISTORY("上游步骤输出与历史事件摘要"),
    TOOL_RESULTS("工具调用受控输出与执行证据"),
    USER_INPUT("用户显式需求指令与确认参数");

    private final String description;

    ContextLayerType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
