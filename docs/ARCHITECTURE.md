# AgentHub 核心架构设计说明书

本文档系统阐述 AgentHub 平台的领域建模、四层架构分层规范、SPI 插件机制与并发控制模型。

> 核心架构决策请参阅 [ADR-001 ~ ADR-004](adr/ADR-001-modular-monolith-architecture.md)。  
> 当前系统经过代码验证的真实边界与局限请参阅 [《当前真实能力边界与架构现状白皮书》](CURRENT_CAPABILITY_BOUNDARIES.md)。

---

## 一、 领域驱动设计 (DDD) 四层分层架构

AgentHub 严格遵循 DDD 经典四层分层结构，确保核心领域规则的高内聚与低耦合：

```mermaid
flowchart TD
    subgraph Adapter Layer [用户接口与适配层]
        IMCtrl[IMController]
        WfCtrl[WorkflowAndDiffController]
        SandboxCtrl[SandboxController]
        RegCtrl[AgentRegistryController]
        HealthCtrl[SystemHealthController]
    end

    subgraph Application Layer [应用服务层]
        IMService[IMCollaborationService]
        WfService[WorkflowEngineService]
    end

    subgraph Domain Layer [核心业务领域层]
        SPI[UnifiedAgentAdapter SPI]
        Git[JGitWorkspaceManager]
        Decomposer[OrchestratorTaskDecomposer]
        Mention[MentionParser]
        Models[Domain Models / Entities]
    end

    subgraph Infrastructure Layer [基础设施层]
        CliAdapter[CliProcessAdapter]
        ApiAdapter[SpringAiApiAdapter]
        LockMgr[WorkspaceLockManager]
        Repo[JPA Repositories / H2 / MySQL]
    end

    Adapter Layer --> Application Layer
    Application Layer --> Domain Layer
    Domain Layer -.-> Infrastructure Layer
    Infrastructure Layer --> Repo
```

### 1. 适配层 (Adapter Layer)
- **包路径**：`com.agenthub.adapter`
- **职责**：处理协议转换、入参校验与全局异常兜底。
  - `web/`：包含 RESTful API 与 SSE 长连接端点；
  - `common/`：提供企业级统一响应体 `Result<T>`、业务异常 `BusinessException` 与全局统一异常拦截器 `GlobalExceptionHandler`。

### 2. 应用层 (Application Layer)
- **包路径**：`com.agenthub.application`
- **职责**：组织业务用例与编排跨领域实体的协作流程。
  - `IMCollaborationService`：负责协同会话的生命周期管理、SSE Emitter 维护、群聊事件分发及 @Mention 智能路由。

### 3. 领域层 (Domain Layer)
- **包路径**：`com.agenthub.domain`
- **职责**：封装核心业务逻辑与规则实体。
  - `agent/`：定义 `UnifiedAgentAdapter` SPI 核心接口与智能体能力元数据；
  - `conversation/`：定义群聊会话模型、富文本交互卡片 `InteractiveCard` 与 Orchestrator 任务拓扑拆解器；
  - `workflow/`：基于有向无环图（DAG）的状态机引擎与节点流转规则；
  - `workspace/`：受控工作区安全解析器（`WorkspaceResolver`，参见 ADR-003）与基于 Eclipse JGit 内核的原生版本控制引擎与行级 Unified Diff 增删行统计器；
  - `sandbox/`：Web 页面静态模版预览与 Dockerfile 部署清单生成器（参见 CURRENT_CAPABILITY_BOUNDARIES.md）。

### 4. 基础设施层 (Infrastructure Layer)
- **包路径**：`com.agenthub.infrastructure`
- **职责**：为上层提供具体的外部 IO、进程管道与存储支撑。
  - `adapter/`：支持本地命令行运行时（Claude Code、Codex、OpenClaw）的非阻塞进程管道包装，以及 DeepSeek V3 API 标准安全 HTTP 适配器；
  - `concurrency/`：基于可重入读写锁与超时看门狗机制的 `WorkspaceLockManager`，防止多 Agent 协作时对同一代码工作区的读写冲突；
  - `repository/`：Spring Data JPA 实体映射与持久化仓储。

---

## 二、 统一智能体适配器 (Unified SPI) 机制

平台通过 `UnifiedAgentAdapter` 抽象将异构 Agent 运行时解耦为标准契约：

```java
public interface UnifiedAgentAdapter {
    AgentPlatformType getSupportedPlatform();
    boolean isAvailable();
    String checkVersion();
    AgentExecutionResult execute(AgentExecutionRequest request);
    void executeStream(AgentExecutionRequest request, Consumer<String> onChunk, Consumer<AgentExecutionResult> onComplete);
}
```

- **本地 CLI 进程安全**：通过 `ProcessBuilder` 构造非阻塞流，内置 Windows / POSIX 命令行差异抹平、超时强制回收看门狗与敏感 Token 正则脱敏过滤；
- **优雅降级与模拟边界**：当宿主机未安装特定 CLI 或未配置 API Key 时，支持在开发/测试环境下回退至模拟器演示，执行结果强制标记 `simulated=true`；生产环境严格禁用降级（详见 [ADR-004](adr/ADR-004-mock-runtime-for-test-and-degraded-demo.md)）。

---

## 三、 基于 JGit 的工作区版本审计机制

不同于前端基于字符串的轻量视觉比对，AgentHub 采用原生 Eclipse JGit 核心驱动：

1. **工作区基线初始化**：在多 Agent 协同介入前，系统自动在物理目录初始化 Git 仓库并创建 Baseline Commit；
2. **多阶段增量快照**：各 Agent 产出代码后，JGit 对比 `HEAD^{tree}` 与实际物理文件树 `FileTreeIterator`；
3. **输出标准 Unified Diff**：准确计算添加行数、删除行数与标准 Git Patch 格式，无缝衔接企业级 CI/CD 审查流水线。
