# AgentHub 架构说明

> 本文是当前架构的事实摘要，不承担长期路线、API 全量契约或技术答辩材料职责。长期路线见 [产品化长期计划](AGENTHUB_PRODUCT_LONG_TERM_PLAN.md)，接口细节见 [API 契约](API.md)，边界和证据见 [当前能力边界](CURRENT_CAPABILITY_BOUNDARIES.md)。

## 1. 架构定位

AgentHub 当前采用 Java 17 + Spring Boot 3.3.4 的模块化单体。所有核心业务共享一个事务数据库和一个应用进程；只有在真实负载或隔离需求触发后，才考虑把执行 Worker、沙箱或事件广播拆出。

模块按业务内聚性组织：

| 模块 | 责任 | 主要事实 |
| --- | --- | --- |
| `identity` | 登录、令牌、资源授权（`ResourceOwnershipVerifier`）、认证审计 | User、Token、AuthAudit |
| `project` | Project、Workspace、单一 `/api/workspaces/{workspaceId}` 资源族和项目归属 | Project、Workspace |
| `orchestration` | Workflow DSL、拓扑校验、审批门禁 | WorkflowDefinition、Approval |
| `execution` | Run/Step 状态、租约、恢复、事件流与证据包（内部拆分为 `RunCommandModule`、`RunQueryModule`、`RunEventStreamModule`、`RunEvidenceModule`） | WorkflowRun、StepRun、RunEvent |
| `agent` / `runtime` | Agent 注册、Provider 路由、Runtime SPI、用量和脱敏 | AgentDefinition、Provider、TokenUsage |
| `audit` | Artifact、JGit 结构化 Diff、审查和回滚 | Artifact、Evidence |
| `sandbox` | 受控构建、启动、探针、停止和清理 | Deployment |
| `ops` | 系统健康、API v1 契约元数据、OpenAPI 快照、旧接口迁移遥测、指标与冷备恢复 | SystemHealth、ApiContract、BackupManifest |
| `team` / `conversation` | 多 Agent 协作和消息上下文（隔离协作扩展） | Team、Conversation、Message |
| `infrastructure` | JGit、跨实例锁、指标和通用技术组件 | 共享技术能力 |

Controller 只负责协议转换和权限入口；Application 模块编排用例；Domain 模块承载状态和规则；Infrastructure 模块实现数据库、文件、Provider、进程和外部系统适配。Controller 不得直接注入 Repository、不得暴露 JPA Entity，也不得直接操作 `java.nio.file.Path`、`JGitWorkspaceManager` 或 `WorkspaceLockManager`（见 [ADR-006](adr/ADR-006-context-convergence-and-api-contract-v1.md)）。
`DagExecutionEngine` 对外保持单一执行入口，内部拆分为 `DagTopologyPlanner`、`DagLeaseExecutor`、`DagStateTransitioner`、`DagAgentInvoker`、`DagEventRecorder` 五个 seam。

## 2. 核心资源与事件词汇表 (Resource & Event Vocabulary)

发现代码与历史文档存在命名差异时，以实际持久化模型（Flyway V1～V12 + JPA Entity）与 REST/SSE 契约为唯一权威基准：

| 词汇概念 | Owner 模块 | 持久化表 / JPA Entity | 权威 API 入口 | 状态枚举 / 核心不变量 | 当前实现与演进说明 |
| --- | --- | --- | --- | --- | --- |
| `Project` | `project` | `projects` / `ProjectEntity` | `/api/projects` | `ACTIVE`, `ARCHIVED`；强制绑定 `owner_id` 与默认 `workspace_id` | 已落地；所有下属资源通过 `Project.ownerId` 校验归属 |
| `Workspace` | `project` | `workspaces` / `WorkspaceEntity` (+ `workspace_locks`) | `/api/workspaces/{workspaceId}/*` | 仅接受 `workspaceId` + 受检相对路径；JGit 维护 `git_baseline_commit` | 已落地；旧 `/api/workspace/*` 在 `prod` 已禁用，计划 Phase 1 删除 |
| `WorkflowDefinition` | `orchestration` | `workflow_definitions` / `WorkflowDefinitionEntity` | `/api/workflows/definitions` | Kahn 拓扑校验无环；含 `version`、`schema_version`、`dsl_json`、`checksum` | 已落地；Phase 3 将演进为绑定 `AgentDefinition` 快照的 `WorkflowTemplate` |
| `Run` (`WorkflowRun`) | `execution` | `workflow_runs` / `WorkflowRunEntity` | `/api/executions/runs`（别名 `/api/runs`） | `PENDING`, `RUNNING`, `PAUSED`, `WAITING_APPROVAL`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT` | 已落地；创建时冻结 `dsl_snapshot`，支持 `idempotency_key` 与租约字段 |
| `StepRun` | `execution` | `step_runs` / `StepRunEntity` | `/api/executions/runs/{runId}/steps` | 状态同 `WorkflowRun` 外加 `SKIPPED`；含 `attempt`、`lease_owner`、`lease_until` | 已落地；重启恢复时已 `SUCCEEDED` 节点直接复用持久化输出，不重跑 |
| `Event` (`RunEvent` / `ToolCall`) | `execution` | `run_events` / `RunEventEntity` | `/api/executions/runs/{runId}/events` & `/stream` | 唯一约束 `uk_run_events_run_seq (run_id, sequence_num)`；含 `STEP_*`、`TOOL_CALL` 等事件，先落库后推送 SSE | 已落地；与 IM 会话的 `messages` 表物理隔离，分别服务于执行审计与协作 |
| `Artifact` | `audit` | `artifacts` / `ArtifactEntity` | `/api/audit/artifacts/*` | `review_status`: `PENDING`, `ACCEPTED`, `REJECTED`, `REVERTED`；含 SHA-256 `checksum` | 已落地；绑定 `run_id` 与 JGit 快照 commit，支持非破坏性文件级 `revert` |
| `Approval` | `orchestration` | `approvals` / `ApprovalEntity` | `/api/approvals/*` | `status`: `PENDING`, `APPROVED`, `REJECTED`；阻塞并唤醒 `WAITING_APPROVAL` 节点 | 已落地；审批决定写入 `decision_reason` / `comments` 并触发 DAG 续跑或终止 |
| `Deployment` | `sandbox` | `deployments` / `DeploymentEntity` | `/api/deployments` | `CREATED -> BUILD -> START -> HEALTH_CHECK -> RUNNING -> STOP -> CLEANUP -> STOPPED / FAILED` | 已落地；强制要求 `Artifact` 为 `ACCEPTED` 且通过真实 HTTP 探针才进入 `RUNNING` |
| `Agent Policy` (`AgentDefinition`) | `agent` / `sandbox` | `agent_definitions` + `providers` + `CommandSecurityGuard` | `/api/agents`, `/api/providers` | 包含 `preferred_provider_type`、`required_capabilities`、`fallback_enabled` 及工具/命令白名单 | 当前分散在 `AgentDefinitionEntity` 与沙箱守卫中；Phase 3 收敛为版本化策略模型 |
| `Context Source` | `execution` / `conversation` | `workflow_runs.context_data_json` + `conversations.summary` | `/api/conversations/{id}/context` | 包含 DAG 节点输入输出插值、滑动窗口裁剪（`TokenBudgetContextManager`）与滚动摘要 | 当前支持会话窗口与运行上下文；Phase 2/3 将扩展为带版本、安全级别与预算的受控资源 |

## 3. 核心数据流

```mermaid
flowchart LR
    U[用户/前端] --> A[Auth + Resource Guard]
    A --> P[Project / Workspace]
    A --> W[Workflow Definition]
    W --> R[Create Run: immutable snapshot]
    R --> E[Execution Kernel]
    E --> S[Step lease + state machine]
    S --> T[Agent Runtime / Tools]
    T --> G[JGit workspace]
    S --> V[Run Events: persist before emit]
    G --> D[Diff / Artifact / Quality Gates]
    D --> H[Approval]
    H --> X[Sandbox Deployment]
    X --> B[Evidence Bundle]
```

Run 创建后冻结 DSL、输入、Provider/Runtime 策略、工作区基线、权限和预算快照。前端的 DAG、终端、审批、Diff 和预览都是这些持久化事实的投影。

## 4. 执行和事件语义

`DagExecutionEngine` 是生产执行内核。Run/Step 通过数据库条件更新和租约字段领取，`PersistentExecutionRecoveryRunner` 在应用启动或租约过期后恢复未完成工作。已成功的 Step 从持久化输出恢复，不能因 JVM 重启而重复产生副作用。

执行语义是 at-least-once 调度加幂等副作用：每次尝试有 attempt、租约、超时、取消和审计记录；系统不宣称分布式 exactly-once。状态迁移必须经过统一状态机，不能由多个线程直接改 Entity 字段。

RunEvent 先持久化，再推送给 SSE。客户端使用 `Last-Event-ID` 或游标补回断线期间的事件。当前活跃 SSE 连接、取消令牌和审批唤醒仍有单节点内存部分，多实例广播属于后续阶段能力。

## 5. 工作区、Diff 和部署

Workspace API 只接受服务端 `workspaceId` 与受检相对路径。`DefaultWorkspaceResolver` 阻断绝对路径、目录穿越、符号链接逃逸和受保护目录；JGit 保存基线、快照、结构化 Diff 和回滚记录。并发写入由数据库租约、JVM 锁和 OS 文件锁共同保护。

Deployment 只能绑定已接受 Artifact 和受控模板，生命周期为：

```text
CREATED -> BUILD -> START -> HEALTH_CHECK -> RUNNING
                                  |             |
                                  v             v
                                FAILED       STOP -> CLEANUP -> STOPPED
```

只有真实 HTTP 探针通过后才进入 `RUNNING`。Local Process 和 Docker sandbox 都属于受控团队内部预览能力；当前没有 MicroVM、Kubernetes 或公网不可信代码的内核级隔离。

## 6. Agent 和上下文

Provider 通过 `LlmProvider` SPI 接入，路由按能力、优先级、成本、健康度和熔断状态选择。Provider、Runtime、工具调用和降级均必须写入 Run 证据；Mock 只能属于明确的 Demo/Test 模式。

长期演进会把 Agent 收敛为版本化 `AgentDefinition` 和 `WorkflowTemplate`：包含目标、工具 allowlist、上下文来源、Token 预算、成本上限、重试、超时和停止条件。上下文来源包括项目规则、AOCI 认知、代码文件、Run 事件摘要和工具输出，均需绑定版本、范围和安全级别。

## 7. 数据库表全景矩阵与可观测性 (Flyway V1～V12 共 22 张表)

- Flyway 是唯一 Schema 演进方式；生产使用 `ddl-auto=validate`。
- Micrometer/Prometheus 暴露 Run、Step、Provider、SSE、锁和 Deployment 指标；日志必须结构化并脱敏。
- 证据包使用脱敏内容、模式标识和确定性 checksum；证据包只能证明对应 Run 的事实，不能替代目标环境验证。

| 数据库表名 | Owner 模块 | JPA Entity / 访问组件 | 来源迁移 | 认证与归属隔离 | 幂等 / 唯一约束 | 状态 | 核心验证测试 | 删除 / 演进计划 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `users` | `identity` | `UserEntity` | `V1`, `V2` | 自身为主体 (`id`) | `PRIMARY KEY(id)`, `UNIQUE(email)` | `ACTIVE` | `RestApiSecurityAndMultiTenancyIntegrationTest` | 长期保留；`V2` 演示种子在 Phase 1 迁入 demo profile |
| `invalidated_tokens` | `identity` | `InvalidatedTokenEntity` | `V9` | `user_id` 关联当前用户 | `PRIMARY KEY(token_id)` | `ACTIVE` | `PhaseASecurityAndResourceBoundaryIntegrationTest` | 长期保留；过期记录按 `expires_at` 清理 |
| `auth_audit_logs` | `identity` | `AuthAuditLogEntity` | `V9` | 记录 `user_id`、`email`、`ip_address` | `PRIMARY KEY(id)` | `ACTIVE` | `PhaseASecurityAndResourceBoundaryIntegrationTest` | 长期保留；只追加审计表 |
| `projects` | `project` | `ProjectEntity` | `V1`, `V2` | `owner_id` (`ResourceAccessGuard`) | `PRIMARY KEY(id)` | `ACTIVE` | `PhaseASecurityAndResourceBoundaryIntegrationTest` | 长期保留；`proj-default` 种子在 Phase 1 迁入 demo profile |
| `workspaces` | `project` | `WorkspaceEntity` | `V1`, `V2` | 经 `project_id -> projects.owner_id` 校验 | `PRIMARY KEY(id)` | `ACTIVE` | `WorkspaceSecurityAndJGitAuditIntegrationTest` | 长期保留；`ws-default` 种子在 Phase 1 迁入 demo profile |
| `workspace_locks` | `infrastructure` | `WorkspaceLockManager` (JDBC) | `V3`, `V12` | `owner_id` 服务端从认证上下文派生 | `PRIMARY KEY(workspace_key)` + `expires_at_epoch_ms` 条件更新 | `ACTIVE` | `PhaseEProductionOpsAndDisasterRecoveryIntegrationTest` | 长期保留；跨实例毫秒级租约互斥 |
| `agent_definitions` | `agent` | `AgentDefinitionEntity` | `V1`, `V2`, `V6` | 系统级模板，调用需登录 | `PRIMARY KEY(id)`, `UNIQUE(def_key)` | `ACTIVE` | `ProviderDrivenAgentRuntimeIntegrationTest` | Phase 3 演进为带工具权限、上下文来源与预算的版本化定义 |
| `agent_instances` | `agent` | `AgentInstanceEntity` | `V1` | `owner_id` + `project_id` | `PRIMARY KEY(id)` | `ACTIVE` | `FlywayMigrationAndSchemaTest` | Phase 3 与版本化 `AgentDefinition` 快照合并收敛 |
| `providers` | `agent` | `ProviderEntity` | `V1`, `V6` | `owner_id` (`ResourceAccessGuard`) | `PRIMARY KEY(id)` | `ACTIVE` | `ProviderAndTokenUsageControllerIntegrationTest` | 长期保留；`secret_ref` 脱敏存储，`V6` 默认种子 Phase 1 隔离 |
| `token_usages` | `agent` | `TokenUsageAuditEntity` | `V6` | 经 `run_id -> workflow_runs` 校验 | `PRIMARY KEY(id)` | `ACTIVE` | `TokenCostAccountingAndAuditingTest` | 长期保留；区分真实 Provider 计费与 `DEMO_SIMULATED` |
| `teams` | `team` | `TeamEntity` | `V1`, `V7` | 经 `project_id` 校验归属 | `PRIMARY KEY(id)` | `ISOLATED_EXT` | `TeamAndConversationControllerIntegrationTest` | 作为协作扩展隔离维护，不阻塞研发变更交付主线 |
| `team_members` | `team` | `TeamMemberEntity` | `V1`, `V7` | 经 `team_id -> teams` 校验 | `PRIMARY KEY(id)` | `ISOLATED_EXT` | `TeamTopologyAndCoordinationTest` | 作为协作扩展隔离维护 |
| `conversations` | `conversation` | `ConversationEntity` | `V1`, `V7` | `owner_id` + `project_id` 校验 | `PRIMARY KEY(id)` | `ISOLATED_EXT` | `ConversationEntityRelationAndMessageSchemaTest` | Phase 1 移除 Entity 默认 `user-1`/`proj-default` 赋值 |
| `conversation_participants` | `conversation` | `ConversationParticipantEntity` | `V1` | 经 `conversation_id` 校验 | `PRIMARY KEY(id)` | `ISOLATED_EXT` | `MessageRoutingAndVisibilityIsolationTest` | 作为协作扩展隔离维护 |
| `messages` | `conversation` | `MessageEntity` | `V1`, `V7` | 经 `conversation_id` + `MessageVisibilityFilter` | `PRIMARY KEY(id)`, `(conversation_id, sequence_num)` 单调递增 | `ISOLATED_EXT` | `ConversationMonotonicSequenceAndConcurrencyTest` | 作为协作扩展隔离维护 |
| `workflow_definitions` | `orchestration` | `WorkflowDefinitionEntity` | `V1`, `V5`, `V10` | 登录鉴权 + 项目关联校验 | `PRIMARY KEY(id)` + `checksum` | `ACTIVE` | `WorkflowDefinitionAndApprovalControllerIntegrationTest` | 长期保留；Phase 3 升级为 `WorkflowTemplate` |
| `workflow_runs` | `execution` | `WorkflowRunEntity` | `V1`, `V4`, `V5`, `V10` | 经 `project_id -> projects.owner_id` 校验 | `PRIMARY KEY(id)` + `idempotency_key` 防重 + `lease_until` 抢占 | `ACTIVE` | `PersistentExecutionRecoveryAndLeaseTest` | 长期保留；执行事实唯一权威来源 |
| `step_runs` | `execution` | `StepRunEntity` | `V1`, `V4`, `V5`, `V10` | 经 `run_id -> workflow_runs` 校验 | `PRIMARY KEY(id)` + `(run_id, node_id)` + `attempt` | `ACTIVE` | `PersistentExecutionRecoveryAndLeaseTest` | 长期保留；节点级状态机与租约载体 |
| `run_events` | `execution` | `RunEventEntity` | `V1`, `V10` | 经 `run_id -> workflow_runs` 校验 | `PRIMARY KEY(id)`, `UNIQUE uk_run_events_run_seq(run_id, sequence_num)` | `ACTIVE` | `ExecutionSseStreamAndReconnectionTest` | 长期保留；SSE 断点续传与证据包事件源 |
| `artifacts` | `audit` | `ArtifactEntity` | `V1`, `V3` | 经 `run_id -> workflow_runs` 校验 | `PRIMARY KEY(id)` + SHA-256 `checksum` | `ACTIVE` | `WorkspaceSecurityAndJGitAuditIntegrationTest` | 长期保留；Phase 5 扩展为完整 Artifact Lineage |
| `approvals` | `orchestration` | `ApprovalEntity` | `V1`, `V5` | 经 `run_id -> workflow_runs` 校验 | `PRIMARY KEY(id)`；单次决策状态转换 | `ACTIVE` | `HumanInTheLoopApprovalIntegrationTest` | 长期保留；人工审批门禁权威记录 |
| `deployments` | `sandbox` | `DeploymentEntity` | `V1`, `V8`, `V11` | 经 `project_id -> projects.owner_id` 校验 | `PRIMARY KEY(id)` + 受控端口池原子分配 | `ACTIVE` | `PhaseDSandboxLifecycleAndDeliveryIntegrationTest` | 长期保留；绑定已验收 `artifact_id` 与健康探针轨迹 |

## 8. 架构守卫和扩展规则

ArchUnit（`ArchUnitArchitectureTest`）与 Phase 0 基线守卫（`Phase0BaselineAndDebtFreezeGuardTest`）联合守护分层依赖、Controller 位置、Entity 泄露、废弃组件零新增调用、前端零 `localhost:8080` 直连及默认 ID 冻结。新增 Provider、节点类型、工具或部署模板必须同时提供真实用户旅程、权限模型、成本边界、契约测试、可观测字段和删除条件。没有真实使用者的适配器进入瘦身清单，不继续堆叠。

详细取舍保存在 [ADR-001](adr/ADR-001-modular-monolith-architecture.md) 至 [ADR-005](adr/ADR-005-database-driven-lease-and-crash-recovery.md)。

