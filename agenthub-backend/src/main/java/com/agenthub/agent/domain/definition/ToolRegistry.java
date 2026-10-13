package com.agenthub.agent.domain.definition;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolRegistry {

    private final Map<String, AgentToolDefinition> tools = new ConcurrentHashMap<>();

    public ToolRegistry() {
        registerDefaultTools();
    }

    private void registerDefaultTools() {
        register(new AgentToolDefinition(
                "file_read",
                AgentToolCategory.FILE_READ,
                "受控读取工作区指定文件文本内容",
                false,
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"工作区相对路径\"}},\"required\":[\"path\"]}",
                2 * 1024 * 1024 // 2MB
        ));

        register(new AgentToolDefinition(
                "file_write",
                AgentToolCategory.FILE_WRITE,
                "受控写入或覆盖工作区文件（高影响工具，需计划授权）",
                true,
                "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}",
                10 * 1024 * 1024 // 10MB
        ));

        register(new AgentToolDefinition(
                "file_tree",
                AgentToolCategory.FILE_READ,
                "查看工作区目录结构树",
                false,
                "{\"type\":\"object\",\"properties\":{\"subpath\":{\"type\":\"string\"}}}",
                512 * 1024 // 512KB
        ));

        register(new AgentToolDefinition(
                "command_exec",
                AgentToolCategory.COMMAND_EXEC,
                "在受控工作区中执行构建或测试命令（高影响工具，需计划授权）",
                true,
                "{\"type\":\"object\",\"properties\":{\"command\":{\"type\":\"string\"},\"timeoutSeconds\":{\"type\":\"integer\"}},\"required\":[\"command\"]}",
                1024 * 1024 // 1MB
        ));

        register(new AgentToolDefinition(
                "git_operation",
                AgentToolCategory.GIT_OPERATION,
                "受控创建 Git 分支、查看 diff 或生成 commit 快照（高影响工具，需计划授权）",
                true,
                "{\"type\":\"object\",\"properties\":{\"action\":{\"type\":\"string\",\"enum\":[\"diff\",\"commit\",\"branch\",\"reset\"]},\"message\":{\"type\":\"string\"}},\"required\":[\"action\"]}",
                1024 * 1024 // 1MB
        ));

        register(new AgentToolDefinition(
                "deploy_preview",
                AgentToolCategory.DEPLOYMENT,
                "启动受控工作区静态或微服务预览沙箱（高影响工具，需计划授权）",
                true,
                "{\"type\":\"object\",\"properties\":{\"target\":{\"type\":\"string\"},\"port\":{\"type\":\"integer\"}},\"required\":[\"target\"]}",
                512 * 1024 // 512KB
        ));

        register(new AgentToolDefinition(
                "network_request",
                AgentToolCategory.NETWORK_REQUEST,
                "受限外网数据拉取与外部 API 网络调用（高影响工具，需计划授权）",
                true,
                "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"},\"method\":{\"type\":\"string\",\"enum\":[\"GET\",\"POST\"]}},\"required\":[\"url\"]}",
                1024 * 1024 // 1MB
        ));

        register(new AgentToolDefinition(
                "code_analyze",
                AgentToolCategory.CODE_ANALYSIS,
                "执行静态代码分析、圈复杂度统计与 AST 语法审查",
                false,
                "{\"type\":\"object\",\"properties\":{\"paths\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}",
                512 * 1024 // 512KB
        ));
    }

    public void register(AgentToolDefinition tool) {
        if (tool != null && tool.getName() != null) {
            tools.put(tool.getName(), tool);
        }
    }

    public Optional<AgentToolDefinition> findByName(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(tools.get(name));
    }

    public List<AgentToolDefinition> listAllTools() {
        return new ArrayList<>(tools.values());
    }

    public boolean isHighImpact(String toolName) {
        AgentToolDefinition def = tools.get(toolName);
        return def != null && def.isHighImpact();
    }

    public String guardOutputSize(String toolName, String rawOutput) {
        if (rawOutput == null) return "";
        AgentToolDefinition def = tools.get(toolName);
        long limit = def != null ? def.getMaxOutputSizeBytes() : 1024 * 1024;
        byte[] bytes = rawOutput.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= limit) {
            return rawOutput;
        }
        int truncatedLen = (int) Math.min(bytes.length, limit);
        while (truncatedLen > 0 && (bytes[truncatedLen - 1] & 0xC0) == 0x80) {
            truncatedLen--;
        }
        if (truncatedLen > 0 && (bytes[truncatedLen - 1] & 0x80) != 0) {
            truncatedLen--;
        }
        String truncatedText = new String(bytes, 0, truncatedLen, java.nio.charset.StandardCharsets.UTF_8);
        return truncatedText + "\n[TRUNCATED: Output exceeded max allowed size of " + limit + " bytes for tool " + toolName + "]";
    }
}
