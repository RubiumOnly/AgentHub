# AgentHub 核心架构设计说明书

本文档系统阐述 AgentHub 平台的领域建模、模块化单体包结构、四层架构分层规范、SPI 插件机制、Flyway 持久化基线与架构防腐守护。

> 核心架构决策请参阅 [ADR-001 ~ ADR-004](adr/ADR-001-modular-monolith-architecture.md)。  
> 长期产品化演进路线请参阅 [《AgentHub 长期产品化与简历项目实施计划》](AGENTHUB_LONG_TERM_PLAN.md)。  
> 当前系统经过代码验证的真实边界与局限请参阅 [《当前真实能力边界与架构现状白皮书》](CURRENT_CAPABILITY_BOUNDARIES.md)。

---

## 一、 领域驱动设计 (DDD) 模块化单体与分层架构

AgentHub 采用**模块化单体 (Modular Monolith)** 架构形态，严格划分 9 大业务领域模块与通用支撑模块，杜绝技术层大杂烩堆砌。

```mermaid
flowchart TD
    subgraph Client [前端与客户端]
        Web[Next.js 14 Bento Console]
        CLI[API / CLI Consumers]
    end

    subgraph SecurityFilter [安全与请求链路治理]
        CorrFilter[RequestCorrelationFilter (X-Request-ID/X-Correlation-ID)]
        AuthFilter[AuthFilter (Bearer Token / Dev Token 解析)]
        Guard[ResourceAccessGuard (跨租户资源归属越权防御)]
    end

    subgraph API [用户接口适配层 (Controllers)]
        AuthCtrl[AuthController]
        ProjCtrl[ProjectController]
        WorkspaceCtrl[WorkspaceController]
        ArtifactCtrl[ArtifactController]
        ExecCtrl[ExecutionController]
        IMCtrl[IMController]
        WfCtrl[WorkflowAndDiffController]
        AgentCtrl[AgentRegistryController]
        SandboxCtrl[SandboxController]
        SysCtrl[SystemHealthController]
    end

    subgraph Application [应用服务层 (Application Services & Ports)]
        AuthApp[AuthApplication]
        ProjApp[ProjectApplication / WorkspaceApplication]
        ArtifactApp[ArtifactApplication]
        ConvApp[ConversationApplication]
        ExecApp[ExecutionApplication]
        AgentApp[AgentApplication]
        SandboxApp[SandboxApplication]
    end

    subgraph Domain [九大领域核心 (Domain Models & Ports)]
        Identity[identity: UserEntity, TokenProvider, PasswordEncoder]
        Project[project: ProjectEntity, WorkspaceEntity, PathGuard, Keyed Lock]
        Agent[agent: AgentDefinition, AgentInstance, Provider]
        Conv[conversation: Conversation, Participant, Message(v1)]
        Orch[orchestration: WorkflowDefinition DSL]
        Exec[execution: WorkflowRun, StepRun, RunEvent, StateMachine]
        Audit[audit: Artifact, JGit Baseline, Diff Engine, Safe Revert]
        Sandbox[sandbox: Deployment, Preview]
        Shared[shared: Result, RequestContext, ErrorCode, BusinessException]
    end

    subgraph Persistence [持久化与基础设施层]
        Flyway[Flyway Migrations (V1 Schema / V2 Seed / V3 Audit & Locks)]
        H2MySQL[H2 (MySQL Mode) / MySQL 8.0]
        GitFS[JGit Repository / Controlled Workspaces]
    end

    Client --> SecurityFilter
    SecurityFilter --> API
    API --> Application
    Application --> Domain
    Domain --> Persistence
```

### 1. 九大核心领域模块分布 (`com.agenthub.*`)

- **`identity`**：用户登录、注册、密码哈希（BCrypt）、Token 鉴权（HMAC-SHA256 与开发兼容模式）、跨租户资源归属校验（`ResourceAccessGuard`）；
- **`project`**：项目边界（`ProjectEntity`）、受控代码工作区（`WorkspaceEntity`）、受控路径解析与目录树安全提取；
- **`agent`**：智能体平台能力定义（`AgentDefinition`）、实例配置（`AgentInstance`）、模型提供商（`Provider`）；
- **`conversation`**：即时通讯会话（`ConversationEntity`）、参会关系表（`ConversationParticipantEntity`，彻底淘汰逗号分割字符串）、版本化与单调递增序号的消息流（`MessageEntity`，支持 `schema_version = "v1"` 与 `sequence_num`）；
- **`orchestration`**：任务编排引擎、版本化工作流 DSL 定义（`WorkflowDefinitionEntity`）；
- **`execution`**：执行实例状态机（`WorkflowRunEntity`，支持幂等键防重）、节点执行（`StepRunEntity`）、可审计与可重放运行时事件（`RunEventEntity`，支持按序号游标拉取）；
- **`audit`**：版本审计基线、JGit 原生 Diff 增删行统计器、交付物实体（`ArtifactEntity`）；
- **`sandbox`**：Web 预览沙箱、Dockerfile 构建清单生成、部署记录（`DeploymentEntity`）；
- **`shared`**：全局统一响应结构 `Result<T>`（携带 `requestId`）、上下文链路传递 `RequestContext`（MDC 追踪）、全域业务异常错误码规范 `ErrorCode`。

### 2. 接口分层与防腐规范 (Controller - Application 隔离)

所有 Web Controller 必须严格遵循以下契约原则：
1. **禁止直接依赖持久化仓储 (Repository)**：Controller 仅能依赖对应领域的 `*Application` 接口；
2. **严禁将 JPA Entity 直接暴露给网络层**：请求参数统一通过 `*Command` / `*Query` 传递，响应体必须映射为 `*View` DTO；
3. **架构守卫自动测试**：通过 ArchUnit 架构测试套件 (`ArchUnitArchitectureTest`) 在 CI/CD 阶段强制执行依赖方向验证。

---

## 二、 数据库版本管理与持久化基线 (Flyway)

系统全面集成 **Flyway** 作为唯一的数据库 Schema 演化机制，杜绝生产环境使用 `hibernate.ddl-auto: update` 带来的脏结构风险。

- **`V1__init_schema.sql`**：统一创建 18 张核心业务表，涵盖 `users`, `projects`, `workspaces`, `agent_definitions`, `agent_instances`, `providers`, `teams`, `team_members`, `conversations`, `conversation_participants`, `messages`, `workflow_definitions`, `workflow_runs`, `step_runs`, `run_events`, `artifacts`, `approvals`, `deployments`；
  - 核心索引：`idx_messages_conv_seq(conversation_id, sequence_num)`, `idx_run_events_run_seq(run_id, sequence_num)`, `idx_wf_runs_idemp(idempotency_key)`；
- **`V2__seed_system_baseline.sql`**：注入系统初始安全基线数据（系统用户、默认项目、默认工作区、四大核心智能体定义：Orchestrator、BackendArchitect、FrontendEngineer、QAAuditor）；
- **`V3__phase2_workspace_audit_artifacts.sql`**：新增产物人工审查状态字段（`review_status`, `reviewed_by`, `reviewed_at`, `review_comment`）以及工作区多实例租约锁持久表 `workspace_locks`；
- **兼容性验证**：Schema DDL 经专门设计，100% 兼容 H2 (MySQL Mode) 本地快速回归测试与生产 MySQL 8.0 严苛验证；
- **事务与查询边界**：生产与测试配置全面启用 `spring.jpa.open-in-view: false`，杜绝因延迟加载穿透导致的隐藏 N+1 查询与事务悬挂问题。

---

## 三、 统一认证鉴权与安全防御体系

1. **链路追踪 (Request Tracing)**：`RequestCorrelationFilter` 自动拦截/分配 `X-Request-ID` 与 `X-Correlation-ID`，并将追踪信息绑定至 MDC 日志与 HTTP 响应头；
2. **身份鉴权 (Authentication)**：基于无状态 Token 令牌机制。生产与测试环境通过 Authorization Header (`Bearer <token>`) 传递，开发环境支持透明安全退化；
3. **水平越权防御 (ResourceAccessGuard)**：执行跨用户资源拦截。任何用户试图操作或查询不属于自己的 Project / Workspace / Conversation 时，一律拦截并返回 HTTP 403 `FORBIDDEN`，杜绝多租户数据泄露。

---

## 四、 基于 ArchUnit 的架构防腐与质量保障

系统引入 ArchUnit 进行代码架构自动化守卫，测试用例 `ArchUnitArchitectureTest` 覆盖以下规则：
- 控制器不得越权直接注入或调用 JPA Repository 仓储；
- 控制器必须收敛于 `adapter.web` 或 `*.api` 包路径下；
- 应用服务必须收敛于 `*.application` 包路径下；
- 控制器严禁直接泄露 JPA 实体对象；
- 73 项单元、领域逻辑与边界防御测试矩阵全量绿灯执行。

---

## 五、 受控工作区、JGit 审计与并发租约治理 (Phase 2 升级)

### 1. 受控工作区路径沙箱解析 (`DefaultWorkspaceResolver`)
- **受控身份体系**：核心文件访问严格基于 `workspaceId + relativePath`，彻底淘汰前端客户端传入绝对路径的越权风险；
- **深层路径防御矩阵**：
  - `Path.normalize()`、`toRealPath()` 与多级父目录 Containment 检验；
  - 符号链接越权检测：禁止通过符号链接跳转至工作区外部目录，违规操作立即抛出 `3004 WORKSPACE_TRAVERSAL_DENIED`；
  - 设备文件拒绝：严格匹配并拦截 Windows 保留设备名称（`CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9` 及其变种文件扩展名）；
  - `.git` 内部篡改拦截：大小写无关（`.GIT`, `.Git`）与尾随点变种防护；
- **受控文件操作与配额保护**：
  - 单文件写入上限硬约束为 10MB；
  - 文本预览上限硬约束为 2MB，超出部分提供截断保护；
  - 二进制文件探测机制（基于扩展名与首 4KB 空字节分析），杜绝乱码污染。

### 2. 原生 JGit 审计引擎与结构化 Diff (`JGitWorkspaceManager`)
- **Run 基线建立**：每次 WorkflowRun 启动时自动初始化工作区 Git 仓库，建立 `Baseline commit for Run <runId>` 并打上专属基线 Tag；
- **Step 快照与 SHA-256 校验和**：节点产出物通过 `createStepSnapshot` 自动归档，记录变更文件清单、commit 哈希与 SHA-256 指纹；
- **结构化 Diff 引擎**：向前端输出 `StructuredDiff`，精准提供文件变更类型（ADD, MODIFY, DELETE, RENAME, COPY）、增减行数、Unified Patch、二进制标识、重命名得分与冲突检测（`hasConflict`）。

### 3. 工作区并发 Keyed Lock 与租约模型 (`WorkspaceLockManager`)
- **锁身份与租期机制**：每个锁必须绑定工作区规范化 Key、执行主体的 `ownerId` 以及租约有效期（`leaseTtlMs`）；
- **防死锁自动回收机制**：若原持有者进程异常崩溃或未能正常释放，超过租约 TTL 后，新请求到达时将自动触发死锁回收（Deadlock Auto-Recovery），保障工作区不会被永久锁死；
- **自动续期与释放保护**：支持长程任务执行中通过 `renewLease` 延长锁租期，并严格校验仅持有者本人方可执行解锁操作。

### 4. 产物审查与安全非破坏性回滚 (`ArtifactApplicationService`)
- **三态审查流程**：支持对 Step 快照产物执行 `accept`、`reject` 与 `revert`，审查记录与决策意见全量归档；
- **受控安全回滚**：
  - 坚决杜绝直接执行破坏性的 `git reset --hard`；
  - 回滚时利用 JGit 仅提取目标 Step 影响的文件还原至基线版本，新创建的文件安全删除，未受影响的文件完整保留；
  - 自动生成明确的 `[Revert]` Commit，确保 Git 历史记录、工作区代码与数据库 Artifact 审查状态高度一致。

---

## 六、 执行内核、状态机与 SSE 流式事件 (Phase 3 升级)

### 1. 严格 Run/Step 状态机 (`ExecutionStateMachine`)
- **八大标准生命周期状态**：
  `PENDING`, `RUNNING`, `PAUSED`, `WAITING_APPROVAL`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`。
- **非法状态转移拦截矩阵**：
  - 终态防御：`SUCCEEDED`, `CANCELLED`, `TIMED_OUT` 为不可逆终态（除重试/重启指令外），任何非法跨态跃迁严格拦截并抛出 `6007 INVALID_STATE_TRANSITION`；
  - 步骤重试：支持从 `FAILED` 与 `TIMED_OUT` 状态通过 `retryStep` 指令重新转移至 `PENDING`，自动递增 `attempt` 并清空旧错误与耗时；
  - 工作流重启：支持工作流从 `FAILED` / `TIMED_OUT` 重启为 `RUNNING`，自动清除旧的 `finishedAt`、`cancelledAt` 与 `cancelReason`；
  - 状态广播归一：状态机内部转移事件全量委托 `RunEventBroadcaster` 发布，确保 `RUN_STATE_CHANGED` 与 `STEP_STATE_CHANGED` 实时直推 SSE，杜绝序号碰撞与失序；
  - 全并发线程安全：使用基于 `runId` 的细粒度锁保障并发转移互斥与幂等一致性，并在执行结束后提供生命周期内存清理。

### 2. 工作流取消与中断协作机制 (`CancelToken` & `CancelTokenRegistry`)
- **协作取消模型**：通过 `CancelToken` 提供非阻塞式取消信号检测（`isCancelled()`, `checkCancelled()` 抛出 `RunCancelledException`）；
- **级联终止协作**：
  - 优雅终止：支持注册自定义清理与回滚回调函数；
  - 跨平台进程树递归强平：结合 `ProcessHandle.descendants()` 递归回收与 Windows `taskkill /PID <pid> /T /F` 双层防御，彻底杜绝孤儿进程残留；
  - 线程级安全中断：对注册的工作线程自动触发 `interrupt()`；
  - 看门狗超时：配置执行超时预算，超出阈值自动触发强平看门狗并将状态收敛为 `TIMED_OUT`。

### 3. 真实执行解耦与 Runtime SPI (`AgentRuntime` & `ExecutionScheduler`)
- **调度与执行分离**：
  - 调度器 `ExecutionScheduler` 专注于节点拓扑编排、状态流转、重试策略、超时看门狗与事件沉淀，杜绝无界线程池 OOM 隐患，采用具名有界线程池及 `@PreDestroy` 优雅停机回收；
  - 实际调用由 `AgentRuntime` SPI 接口完成，彻底解除核心业务对底层大模型或 CLI 命令的具体依赖；
- **多运行时适配矩阵**：
  - `MockAgentRuntime`：离线确定性模拟、测试守护与极速冒烟验证（明确标记 `simulated = true`）；
  - `OpenAiCompatibleRuntime`：兼容 DeepSeek、OpenAI、Moonshot 等大模型标准 HTTP 流式接口，含密钥脱敏与推理内容解析防御；
  - `CliAgentRuntime`：子进程原生运行 Codex CLI / Claude Code / OpenClaw，结合进程树销毁与超时控制；
  - `AgentRuntimeRegistry`：提供运行时动态路由、自动降级与健康检查。

### 4. 实时 SSE 事件流与 Last-Event-ID 断点重连 (`RunEventBroadcaster`)
- **真流式事件输出**：提供专属端点 `GET /api/executions/runs/{runId}/stream` 与 `GET /api/runs/{runId}/stream`，彻底淘汰无状态长轮询假实时；
- **严格单调递增 Sequence**：每个事件均分配唯一的单调递增 `sequenceNum`，并在 `run_events` 表持久化落库；
- **断点补发机制**：客户端携带 `Last-Event-ID` 头部或 `?lastEventId=` 参数重连时，系统自动从数据库按序号精准补齐遗漏的历史事件后再切入实时广播，具备 `MAX_REPLAY_LIMIT (2000)` 上限防 OOM 保护；
- **认证兼容与长连接保活心跳**：`AuthFilter` 支持标准浏览器 `EventSource` URL Token 参数鉴权；内置每 20 秒自动化发送 `:heartbeat` 注释包，防御代理与网关超时断联。


