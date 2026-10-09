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
        DeployCtrl[DeploymentController]
        SysCtrl[SystemHealthController]
    end

    subgraph Application [应用服务层 (Application Services & Ports)]
        AuthApp[AuthApplication]
        ProjApp[ProjectApplication / WorkspaceApplication]
        ArtifactApp[ArtifactApplication]
        ConvApp[ConversationApplication]
        ExecApp[ExecutionApplication]
        AgentApp[AgentApplication]
        SandboxApp[SandboxApplication / DeploymentApplication]
    end

    subgraph Domain [十大领域核心 (Domain Models & Ports)]
        Identity[identity: UserEntity, TokenProvider, PasswordEncoder]
        Project[project: ProjectEntity, WorkspaceEntity, PathGuard, Keyed Lock]
        Agent[agent: AgentDefinition, AgentInstance, Provider]
        Team[team: TeamEntity, TeamMemberEntity, Topology Strategies]
        Conv[conversation: Conversation, MessageBus, Monotonic Seq, Loop Detector]
        Orch[orchestration: WorkflowDefinition DSL]
        Exec[execution: WorkflowRun, StepRun, RunEvent, StateMachine]
        Audit[audit: Artifact, JGit Baseline, Diff Engine, Safe Revert]
        Sandbox[sandbox: SandboxProvider, CommandGuard, PortManager, Deployment]
        Shared[shared: Result, RequestContext, ErrorCode, BusinessException]
    end

    subgraph Persistence [持久化与基础设施层]
        Flyway[Flyway Migrations (V1 Schema / V2 Seed / V3-V8 Evolutions)]
        H2MySQL[H2 (MySQL Mode) / MySQL 8.0]
        GitFS[JGit Repository / Controlled Workspaces]
    end

    Client --> SecurityFilter
    SecurityFilter --> API
    API --> Application
    Application --> Domain
    Domain --> Persistence
```

### 1. 十大核心领域模块分布 (`com.agenthub.*`)

- **`identity`**：用户登录、注册、密码哈希（BCrypt）、Token 鉴权（HMAC-SHA256 与开发兼容模式）、跨租户资源归属校验（`ResourceAccessGuard`）；
- **`project`**：项目边界（`ProjectEntity`）、受控代码工作区（`WorkspaceEntity`）、受控路径解析与目录树安全提取；
- **`agent`**：智能体平台能力定义（`AgentDefinition`）、实例配置（`AgentInstance`）、模型提供商（`Provider`）；
- **`team`**：多智能体团队拓扑协作（`TeamEntity`、`TeamMemberEntity`），支持层级管理 (`HIERARCHICAL`)、对等协作 (`PEER_TO_PEER`) 与轮询流水线 (`ROUND_ROBIN`)，提供细粒度角色分工与委派控制；
- **`conversation`**：即时通讯与消息总线（`ConversationEntity`、`ConversationParticipantEntity`、`MessageEntity`），具备细粒度严格单调递增序号（`sequence_num`）、点对点/广播/系统路由与可见性隔离、三层防循环震荡检测及上下文预算滑动压缩治理；
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
- **`V4__phase3_execution_kernel_and_state_machine.sql`**：扩展 `workflow_runs` 取消原因、时间戳，扩展 `step_runs` 开始/结束时间、耗时字段与链路追踪关联；
- **`V5__phase4_workflow_dag_and_orchestration.sql`**：扩展 `workflow_definitions` (name, description, updated_at), `workflow_runs` (context_data_json), `step_runs` (inputs_json, outputs_json, requires_approval) 及 `approvals` 索引演进；
- **`V6__phase5_agent_providers_and_routing.sql`**：扩展 `providers`（priority, weight, capabilities, cost_per_million_input, cost_per_million_output, circuit_status, avg_latency_ms）与 `agent_definitions` 表，新增 `token_usages` 审计表与多维高效查询复合索引；
- **`V7__phase6_multi_agent_teams_and_message_bus.sql`**：扩展 `teams` 表（topology, leader_agent_id, max_turns, config_json, status），扩展 `team_members` 表（role_type, responsibilities, system_prompt_override, can_delegate），扩展 `conversations` 表（team_id, last_sequence_num, summary, token_count, status），扩展 `messages` 表（recipient_id, message_type, protocol_type, token_count, in_reply_to_id），新增 `idx_messages_recipient`, `idx_messages_type_proto`, `idx_team_members_role`, `idx_conversations_team` 复合索引，并预置 `team-dev-swarm` 经典开发团队种子基线数据；
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
- 121 项单元、领域逻辑、集成演练与边界防御测试矩阵全量绿灯执行。

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

---

## 七、 工作流编排引擎、DAG 依赖拓扑与数据流调度 (Phase 4 升级)

### 1. 版本化工作流 DSL 与严苛图拓扑校验 (`WorkflowDslValidator`)
- **领域规范与核心模型**：
  - `WorkflowDsl`：包含版本标识 (`version`, `schemaVersion`)、全局超时 (`timeoutSeconds`)、入口节点 (`entryNodeId`)、节点集合 (`nodes`) 与有向依赖边集合 (`edges`)；
  - `WorkflowNodeDsl`：节点类型 (`START`, `AGENT`, `CONDITION`, `APPROVAL`, `JOIN`, `END`)、引用智能体 (`agentRef`)、输入映射 (`inputs`)、分支汇聚策略 (`joinPolicy`) 与重试策略 (`RetryPolicyDsl`)；
  - `WorkflowEdgeDsl`：源节点 (`fromNodeId`)、目标节点 (`toNodeId`) 以及动态条件求值表达式 (`condition`)。
- **Kahn 算法拓扑排序与成环检测 (Cycle Detection)**：
  - 基于入度统计的 Kahn 算法实现有向无环图 (DAG) 拓扑分层与环路检测；
  - 严格检测并拦截自环 (Self-loop) 以及多节点回路依赖，违规立即抛出专用业务异常 `6001 WORKFLOW_INVALID` 并附带清晰的成环说明；
  - 孤岛节点与入口节点合法性检验：禁止孤立未连通的游离节点存在，确保所有图结构连通且语义完整。

### 2. 有界线程池真实并发调度内核 (`DagExecutionEngine`)
- **同层兄弟节点真并发**：
  - 调度引擎基于具备容量限制的有界线程池执行兄弟节点，杜绝无界线程池导致的 OOM 风险；
  - 支持多依赖汇聚策略：
    - `all_succeeded`：所有前置入边依赖必须全部成功方可触发当前节点；
    - `any_succeeded`：任一前置依赖完成即满足触发条件；
    - `custom_condition`：结合前置节点输出通过动态表达式判定触发条件；
- **响应式熔断与快速退出机制**：
  - 针对子任务失败、人工拒绝审批或用户主动取消场景，通过 `abortRemainingNodes` 级联熔断后续所有未启动节点；
  - 主循环采用 100ms 快速中断轮询感知机制，彻底消除了下游节点悬挂等待引发的整体任务超时假死。

### 3. 上下游数据流管道与安全无 RCE 表达式解析 (`SafeExpressionEvaluator`)
- **工作流数据管道与上下文隔离 (`WorkflowExecutionContext`)**：
  - 提供线程安全的工作流运行时上下文映射；
  - 动态参数解析管道：支持模板占位符自动解析与点号深度嵌套提取（如 `{{steps.<nodeId>.outputs.<key>}}`、`{{inputs.<key>}}`）；
  - 单节点入参（`inputs_json`）与出参快照（`outputs_json`）结构化持久化落库，保障执行过程完全可观测与可审计。
- **零反射递归下降安全求值引擎 (`SafeExpressionEvaluator`)**：
  - 彻底杜绝使用危险的 Spring EL (SpEL)、OGNL 或 JVM 反射机制，自研基于递归下降（Recursive Descent）词法与语法的 AST 求值引擎；
  - 严格支持布尔与数值比较（`==`, `!=`, `>`, `<`, `>=`, `<=`）、逻辑运算（`&&`, `||`, `!`）及字符串常量安全提取；
  - 内置空指针防御与类型自动转换，从根源消除远程代码执行 (RCE) 与表达式注入隐患。

### 4. 动态条件分支与级联跳过剪枝 (Dynamic Branching & Cascade Skip Pruning)
- **智能条件决策**：在边流转或条件节点求值时，根据上下文变量动态计算 `condition`；
- **未命中分支优雅跳过**：未命中的边所指向的分支节点，由状态机统一标记为 `SKIPPED` 终态；
- **下游级联剪枝**：当节点的必要前置依赖全部被跳过或无法满足激活策略时，引擎自动对所有后继受累节点执行级联剪枝（Cascade Skip），防止任务死锁与悬挂。

### 5. 人工确认门禁与挂起恢复机制 (`Human-in-the-Loop Approval`)
- **敏感/高危节点挂起门禁**：
  - 当节点标记 `requiresApproval: true` 或节点类型为 `APPROVAL` 时，执行调度自动挂起当前 Step 与 Run，状态收敛为 `WAITING_APPROVAL`；
  - 自动向 `approvals` 门禁表插入待审批记录，并通过 SSE 实时广播 `APPROVAL_REQUESTED` 领域事件；
- **REST 审批接口与决策恢复**：
  - 暴露标准接口：`POST /api/approvals/{id}/approve` 与 `POST /api/approvals/{id}/reject`；
  - 审批通过后无缝解冻调度器，恢复下游拓扑节点继续执行；
  - 审批驳回时立即将 Step 状态收敛为 `FAILED`，并级联中止后续节点执行，全链路持久化审查人与驳回意见。

---

## 八、 Agent 统一接入层、多 Provider 与动态路由 (Phase 5 升级)

### 1. Provider SPI 抽象与统一交互契约 (`LlmProvider` & Standard SPI)
- **统一消息与请求响应模型**：
  - `ChatMessage`：标准化抽象角色（`system`, `user`, `assistant`, `tool`）、消息内容与元数据，抹平各家大模型字段异构；
  - `ChatRequest` / `ChatResponse`：标准化请求参数（模型名称、上下文消息、温度系数、最大 Token、能力标签要求、流式开关），响应体严格包含唯一标识、完成原因、物理延迟测量（`latencyMs`）、估算成本以及降级追踪标记（`fallbackUsed`, `originalProviderId`）；
  - `ChatChunk`：标准化流式契约，规范增量内容 (`deltaContent`) 与最终 Token 消耗统计；
- **真实延迟与消耗精确度量**：
  - 执行层精确度量端到端网络与推理物理耗时；
  - 准确统计 `prompt_tokens`、`completion_tokens` 与 `total_tokens`。

### 2. 多 Provider 生态适配矩阵 (Multi-Provider Ecosystem)
- **`OpenAiCompatibleProvider`**：针对 OpenAI (GPT-4o, GPT-4o-mini) 与 DeepSeek (deepseek-chat, deepseek-reasoner) 的标准 `/v1/chat/completions` REST 接口与 SSE 数据流协议；
- **`AnthropicProvider`**：针对 Anthropic Claude (claude-3-5-sonnet, claude-3-haiku) 的 `/v1/messages` 消息协议，实现 system prompt 与 messages 分层抽象及 `content_block_delta` 流式解析；
- **`GeminiProvider`**：针对 Google Gemini (gemini-1.5-pro, gemini-1.5-flash) 的 `generateContent` 与 `streamGenerateContent?alt=sse` REST 协议；
- **`OllamaProvider`**：针对本地 Ollama 与 vLLM 实例原生 `/api/chat` 接口，支持本地离线零成本调度；
- **`MockLlmProvider`**：确定性离线仿真引擎，支持 429 限流、5xx 超时故障注入与微秒级延迟仿真。

### 3. 全链路凭证安全与脱敏治理 (`SecretMasker`)
- **密钥引用与环境变量解耦**：支持 `env:VAR_NAME` 环境变量注入与 `prop:KEY` 配置解耦，数据库 `secret_ref` 绝不明文落库生产密钥；
- **全链路严密脱敏清洗**：
  - API Key 自动脱敏为 `sk-***` 或 `[REDACTED_SECRET]`；
  - 自动识别并过滤网络请求头（`Authorization`, `x-api-key`）与 URL 敏感参数（如 Gemini `?key=...`）；
  - 全链路运行日志与异常堆栈经过正则扫描自动脱敏，REST 控制器与 DTO 绝不向客户端泄露真实密钥。

### 4. 动态路由策略与高可用选择器 (`DynamicProviderRouter`)
- **多维动态加权评分与路由过滤**：
  - **能力匹配 (Capabilities)**：精准匹配任务要求的模型能力（`code`, `general`, `fast`, `reasoning`, `long_context`）；
  - **熔断状态感知**：自动过滤处于 `OPEN` 熔断状态的不健康 Provider；
  - **多维综合排序**：优先按优先级 (`priority` 降序)，同优先级按健康检查延迟 (`latencyMs` 升序) 与权重 (`weight` 降序) 挑选最优 Primary Provider；
- **路由决策预演 (`POST /api/providers/route`)**：支持向平台实时查询任意 prompt 或能力需求下的路由推演结果与完整备份链条。

### 5. 高可用容灾与自动 Fallback 降级 (HA Failover & Circuit Breaking)
- **三态线程安全熔断器 (`CircuitBreaker`)**：
  - 状态转移矩阵：`CLOSED` (正常) -> 连续失败超阈值 -> `OPEN` (阻断熔断) -> 超时探测 -> `HALF_OPEN` (半开探测) -> 成功恢复 `CLOSED` / 失败重回 `OPEN`；
- **自动 Fallback 快速切换**：
  - 当主 Provider 遇到 429 限流、5xx 超时、连接被拒或熔断阻断时，路由引擎自动拦截并无缝切换至备用 Backup Provider；
  - 自动向事件流和运行日志沉淀标准化降级告警事件 `FallbackEvent`，记录原失败节点、接管节点与故障原因。

### 6. Token 与成本计量治理 (Token & Cost Accounting)
- **`ModelPricing` 官方基准牌价与自定义重载**：
  - 内置 GPT-4o, DeepSeek, Claude 3.5 Sonnet, Gemini 1.5 Flash 官方基准百万 Token 牌价；
  - 支持本地 Ollama / vLLM 零成本 ($0.00) 计算；
  - 支持 Provider 数据库记录自定义输入/输出价格精准覆盖；
- **`token_usages` 数据库审计与平台汇总**：
  - 单次 LLM 调用持久化至 `token_usages` 审计表，包含模型、Token 计数、测量延迟与折算美元成本；
  - 提供 `GET /api/token-usages/summary` 与 `/api/token-usages/runs/{runId}` 实时查询端点。

---

## 九、 多智能体协同网络、消息总线与上下文治理体系 (Phase 6 升级)

### 1. 多智能体团队协同拓扑与角色编排 (`TeamTopologyStrategy` & `TeamApplicationService`)
- **三大核心团队协作拓扑**：
  - **`HIERARCHICAL` (层级主从拓扑)**：由 Leader 智能体统一收口用户目标并派发子任务，非 Leader 成员产出默认汇报回传 Leader，防止无组织发散；
  - **`PEER_TO_PEER` (对等去中心拓扑)**：团队成员平级协作，支持自主点对点协作及基于 @Mention 自由触发，无单一瓶颈；
  - **`ROUND_ROBIN` (轮询流水线拓扑)**：智能体严格按照职责链路（如 Architect -> Coder -> Reviewer -> Tester）环形流转，保障软件交付生命周期标准化推进；
- **细粒度角色分工与委派权限 (`TeamRole`)**：
  - 标准化抽象 `ORCHESTRATOR`, `ARCHITECT`, `CODER`, `REVIEWER`, `TESTER`, `CUSTOM` 等角色；
  - 支持成员级 `system_prompt_override`（动态覆盖专家提示词）与 `can_delegate` 权限控制（限制仅特定角色可发起委托或调用下游）。

### 2. 会话消息总线与严格单调递增序号 (`ConversationSequenceManager`)
- **细粒度无碰撞序号保证**：
  - 彻底淘汰客户端或自增键随意并发赋值，采用 128 分段公平重入锁 (`Striped ReentrantLock`) 消除内存泄露隐患，并结合数据库悲观写锁 (`PESSIMISTIC_WRITE`) 保障多实例跨节点并发安全；
  - 严格保障同一会话内所有消息的 `sequence_num` 100% 连续且严格单调递增（1, 2, 3...），从物理层面根除并发写入时的时序颠倒、覆盖与多节点序号碰撞；
  - 支持高并发压测下的并发消息原子分配与自动事务落库。

### 3. 消息路由策略与可见性隔离机制 (`MessageVisibilityFilter`)
- **多元消息路由类型 (`MessageType`)**：
  - **`BROADCAST`**：全员广播消息，会话内所有成员（用户及参与智能体）均可见；
  - **`DIRECT`**：点对点私聊消息，由发送方直接定向推送至 `recipient_id`，天然隔离旁路噪音；
  - **`SYSTEM`**：系统/通知级事件消息，用于声明会话状态变更、拓扑流转或门禁告警；
- **智能可见性过滤矩阵 (`MessageVisibilityFilter`)**：
  - 严格防御私聊泄露：非接收方且非发送方的第三方智能体及未授权匿名请求查询消息流时，自动过滤 P2P 私聊，杜绝信息越权；
  - 发送者自身、目标接收方及管理员具有完整可追溯审计视野。

### 4. 跨智能体协议与四层死循环熔断检测 (`LoopDetector`)
- **标准化交互协议 (`MessageProtocolType`)**：
  - **`REQUEST_REPLY`**：标准请求-响应协作，通过 `in_reply_to_id` 显式关联上下文；
  - **`HANDOFF`**：工作交接协议，实现跨角色控制权让渡；
  - **`SUMMARIZE`**：总结汇报协议，将阶段性结论上报；
- **四层递进式防死循环与震荡熔断防御矩阵**：
  - **第 1 层：连续自主轮次硬截断 (`maxTurns`)**：严格继承团队实体 `maxTurns` 配置，仅统计无人类介入的自主智能体连续交互轮次（人机长对话不误杀），达到上限立即阻断流转并抛出 `4008 CONVERSATION_MAX_TURNS_EXCEEDED`；
  - **第 2 层：高频内容哈希碰撞检测 (`Duplicate Content Collision`)**：对连续发言内容提取规范化哈希指纹，检测到连续重复复读即刻触发循环警报；
  - **第 3 层：短周期双智能体 Ping-Pong 震荡检测 (`Short-cycle Oscillation`)**：实时检测 A -> B -> A -> B 交互序列，识别无意义往返乒乓对射，及时熔断死循环消耗；
  - **第 4 层：三智能体环形振荡与自身重复自旋检测**：精准识别 A -> B -> C -> A -> B -> C 环形协作回路及单智能体 A -> A -> A 连续空转，彻底杜绝复杂多智能体协同下的死循环消耗。

### 5. 智能上下文窗口治理与滑动压缩 (`ContextWindowGovernance`)
- **动态滑动窗口修剪 (`SlidingWindowContextTrimmer`)**：
  - 优先保护系统核心指令（首条系统提示词）与最近 N 条交互高保真上下文；
  - 对历史中间消息执行滑动修剪，输出裁剪统计（`trimmedCount`, `retainedCount`）；
- **Token 预算受限压缩 (`TokenBudgetContextManager`)**：
  - 支持基于最大 Token 预算（`maxBudgetTokens`）严格截断，避免超出大模型上下文硬限制导致调用崩溃；
- **滚动摘要合成引擎 (`RollingSummaryService`)**：
  - 当会话消息超出压缩阈值时，自动提取老旧消息合成为连贯摘要（Summary），持久化于 `conversations.summary`；
  - 下游 Prompt 自动注入 `[Previous Conversation Summary]` + 滑动窗口最新活跃消息，在保留长期记忆的同时节约 70%+ 上下文 Token 成本。

### 6. 会话实时 SSE 事件总线与断点补发 (`ConversationEventBroadcaster`)
- **全生命周期会话事件广播**：
  - 统一推送 `MESSAGE_CREATED`, `MEMBER_JOINED`, `MEMBER_LEFT`, `LOOP_DETECTED`, `CONTEXT_TRIMMED`, `STATUS_CHANGED`, `HEARTBEAT` 等领域事件；
- **基于 Last-Event-ID 的断点续传**：
  - 客户端携带 `Last-Event-ID` 头部或参数重连时，系统基于 `sequence_num` 游标自动从数据库补齐重连期间缺失的所有历史消息事件后再平滑接入实时流；
  - 内置保活心跳（Heartbeat），杜绝网关空闲断联。

---

## 十、 工作区沙箱容器化与部署自动化体系 (Phase 7 升级)

### 1. 沙箱隔离体系架构 (`SandboxProvider` SPI & Providers)
- **统一沙箱 SPI 契约 (`SandboxProvider`)**：
  - 抽象 `execute(SandboxExecutionRequest)`、`destroy(executionId)` 与 `isAvailable()`，解耦上层业务调用与底层沙箱运行环境；
  - 提供 `SandboxProviderFactory`，支持按类型（`LOCAL_PROCESS`, `DOCKER`）解析提供商，并在容器不可用时安全平滑兜底；
- **受限子进程沙箱 (`LocalProcessSandbox`)**：
  - 严格限制 `workingDirectory` 在受控项目工作区内，禁止逃逸；
  - 清空宿主机环境，仅注入经安全脱敏的白名单环境变量；
  - 基于异步并发 `BoundedOutputReader` 与看门狗定时器进行全链路流式审计；
- **容器化沙箱 (`DockerSandbox`)**：
  - 组装非特权安全配置：非 root 用户（`--user 1000:1000`）、只读根文件系统（`--read-only`）；
  - 限制临时写入空间（`--tmpfs /tmp:rw,noexec,nosuid,size=64m`）；
  - 严格限制宿主机挂载：仅挂载受控工作区（`-v <workspace>:/workspace:rw`），禁止宿主机敏感目录或 Docker socket 挂载；
  - 资源上限硬限制：`--memory <quota>m` 与 `--cpus <quota>`。

### 2. 安全策略与命令防火墙 (`CommandSecurityGuard`)
- **高危破坏性命令拦截矩阵**：
  - 阻断破坏性文件删除：`rm -rf /`, `rm -rf ~`, `rm -rf *`, `del /s /q C:\`, `format D:`, `rmdir /s /q C:\`，支持分立参数（`-r -f`, `-f -r`, `--recursive --force`）、带单双引号路径及 PowerShell `Remove-Item -Recurse -Force`；
  - 阻断低级磁盘破坏：`mkfs`, `dd if=...`, `fdisk`, `chmod -R 777 /`；
  - 阻断远程管道脚本注入：`curl ... | sh`, `wget ... | bash`, `curl ... | python`；
  - 阻断恶意命令串联与逃逸：`; rm -rf`, `&& rm -rf`, `|| rm -rf`, PowerShell `-enc/-EncodedCommand`, Fork Bomb (`:(){ :|:& };:`)，主机越权 (`> /dev/sd*`, `> /etc/`)；
  - 深入解析合法 Shell 包裹参数（`sh -c`, `bash -c`, `cmd /c`, `powershell -c`），拦截其内部包裹的恶意命令与脚本；
- **白名单机制 (`Executable Whitelist`)**：
  - 严格放行标准开发工具：`node`, `npm`, `npx`, `yarn`, `pnpm`, `java`, `javac`, `mvn`, `gradle`, `python`, `git`, `echo` 等，并对可执行文件名自动剥离首尾引号及 `.exe`, `.cmd`, `.bat` 后缀；
  - 严格拦截非白名单与黑名单程序：`sudo`, `su`, `useradd`, `nc`, `netcat`, `nmap`, `iptables`, `chroot`。

### 3. 环境变量隔离与敏感凭证防护 (`EnvironmentSanitizer`)
- **宿主机凭证继承阻断**：
  - 自动审查并剔除包含 `KEY`, `SECRET`, `PASSWORD`, `TOKEN`, `CREDENTIAL`, `AUTH`, `DATABASE` 等关键字的宿主机变量；
  - 防止 `OPENAI_API_KEY`, `SPRING_DATASOURCE_PASSWORD`, `JWT_SECRET` 等敏感资产随子进程泄漏；
- **注入攻击拦截**：
  - 仅继承标准系统安全变量（`PATH`, `HOME`, `USER`, `LANG`, `TEMP` 等）；
  - 拦截 `LD_PRELOAD`, `BASH_ENV` 等动态链接劫持注入攻击。

### 4. 资源配额与看门狗超时监控 (`SandboxResourceQuota` & Watchdog)
- **看门狗超时监控 (Watchdog Timeout Kill)**：
  - 单命令执行设定硬超时阈值；
  - 超时自动触发多平台进程树递归强平（Process Tree Kill，包括 Windows 环境 `taskkill /F /T /PID` 级联终止与 Linux 子孙进程级联终止），同步等待句柄完全释放，彻底防止临时文件锁定；进程标记退出码 137 并设置 `isTimedOut = true`；
- **输出缓冲区截断防撑爆 (Output Buffer Truncation)**：
  - 实时限制子进程输出捕获字节上限（默认 1MB，支持自定义）；
  - 缓冲区线程安全并发保护与守护线程池支持；
  - 超出配额时自动截断后续字符，并追加 `[SANDBOX WARNING: Output truncated...]` 警示，彻底消除海量日志输出导致 JVM 内存溢出 OOM 隐患。

### 5. 端口分配管理与健康检查探测 (`PortAllocationService` & `HealthCheckProbeService`)
- **原子受控端口池分配 (`PortAllocationService`)**：
  - 管理 18000-18999 端口池；
  - 结合内存并发原子分配与操作系统底层 TCP ServerSocket 真实绑定探测；
  - 部署结束或失败时自动回收与释放；
- **主动健康检查探测 (`HealthCheckProbeService`)**：
  - 支持对 HTTP 服务进行周期性探测与重试探测；
  - 响应 HTTP 2xx/3xx 判定为健康，探测超时或连接拒绝判定为失败。

### 6. 代码一键部署生命周期与 REST 端点 (`DeploymentApplication` & `DeploymentController`)
- **部署状态机模型 (`DeploymentStatus`)**：
  - `CREATED` -> `BUILDING` -> `RUNNING` / `FAILED` -> `STOPPED`；
- **RESTful 端点矩阵 (`/api/deployments`)**：
  - `POST /api/deployments`：创建并触发构建与部署；
  - `GET /api/deployments/{id}`：查询部署详情与运行状态；
  - `GET /api/deployments/{id}/logs`：获取部署构建与运行日志；
  - `POST /api/deployments/{id}/stop`：主动停止部署并释放占用端口；
  - `GET /api/deployments?projectId={id}`：查询项目历史部署记录。


