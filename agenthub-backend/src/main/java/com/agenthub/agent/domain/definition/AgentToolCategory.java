package com.agenthub.agent.domain.definition;

public enum AgentToolCategory {
    FILE_WRITE(true, "工作区文件受控写入与修改"),
    COMMAND_EXEC(true, "受控进程与构建命令执行"),
    GIT_OPERATION(true, "Git 分支、提交与变更操作"),
    DEPLOYMENT(true, "沙箱构建与受控服务部署"),
    NETWORK_REQUEST(true, "受限外网数据拉取与网络调用"),
    FILE_READ(false, "工作区文件树与文本只读查看"),
    CODE_ANALYSIS(false, "静态代码扫描与 AST 语法分析");

    private final boolean highImpact;
    private final String description;

    AgentToolCategory(boolean highImpact, String description) {
        this.highImpact = highImpact;
        this.description = description;
    }

    public boolean isHighImpact() {
        return highImpact;
    }

    public String getDescription() {
        return description;
    }
}
