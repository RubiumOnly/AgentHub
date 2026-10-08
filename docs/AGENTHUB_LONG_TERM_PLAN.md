# AgentHub 长期产品化与简历项目实施计划

> 文档状态：规划基线 v1.0  
> 适用范围：`D:\work\agenthub` 当前仓库  
> 目标读者：项目作者、后续协作开发者、代码评审者  
> 计划类型：只定义目标、顺序、约束、验收证据和上线路线；不包含本次实施代码

## 1. 项目定位

AgentHub 的最终定位不是“把几个 CLI 接到聊天框里”，而是一个 **Java 原生的多 Agent 软件交付协作平台**：用户用自然语言提出研发目标，平台把目标拆解为可追踪的任务图，调度不同 Agent 运行时在隔离工作区中协作，持续产生消息、日志、代码变更、测试报告和可部署交付物，用户可以审查、批准、回滚并发布结果。

项目应同时回答两个问题：

1. **Agent 应用能力**：如何定义 Agent、编排 Agent、管理上下文、执行工具、处理流式事件、失败重试和人工审批。
2. **Java 后端能力**：如何用 Spring Boot、DDD/模块化单体、并发控制、事务、持久化、异步任务、可观测性和安全边界把上述能力做成可运行系统。

建议在简历中的项目名称使用：

> **AgentHub：面向软件交付的 Java 多 Agent 协作与可审计执行平台**

建议对外主张的差异点是：

- 统一 Agent Adapter 解决异构 Agent CLI/API 接入问题；
- Orchestrator 以有向任务图驱动真实执行，而不是只生成一张静态卡片；
- 每次执行都有独立 Project/Workspace/Run/Step 身份和状态；
- Agent 修改代码必须经过工作区隔离、JGit Diff、测试门禁和人工确认；
- 运行过程可以恢复、取消、重试并留下可审计证据。

以下能力暂不作为主线：社交动态、Agent 市场、移动端、桌面端、复杂组织通讯录。它们可以作为后续扩展，但不能牺牲软件交付主链路。

## 2. 当前基线与验证证据

### 2.1 当前已经具备的资产

当前仓库已经形成可演示骨架：

- 后端使用 Spring Boot 3.3.4、Java 17、Spring MVC、Spring Data JPA、H2/MySQL、JGit；
- `adapter/application/domain/infrastructure` 四层包结构已经建立；
- `UnifiedAgentAdapter` 已抽象 `execute`、`executeStream`、可用性检查和版本检查；
- 已存在 CLI 适配器、DeepSeek HTTP 适配器、降级模拟输出；
- 已有会话、消息、@Mention、SSE、Orchestrator 卡片和工作流模型；
- 已有 JGit 工作区基线、Unified Diff、增删行统计和内存锁；
- 前端已具备聊天大厅、DAG 画布、文件树、文件编辑、Diff、预览和部署入口；
- 根目录文档、CI、Docker Compose、截图和测试矩阵已经存在。

截至本计划编写前的本地验证：

- `agenthub-backend/mvn test`：13 tests，0 failures，0 errors，构建成功；
- `agenthub-frontend/npm run build`：Next.js 14 生产构建成功，类型检查通过；
- 以上结果只能证明当前基线可编译、基础测试可运行，不能证明真实 Agent、生产数据库、权限、安全隔离和部署闭环已完成。

### 2.2 当前方向偏差和必须修正的问题

下面的问题按 P0（必须先修）、P1（产品闭环前修）、P2（增强项）排序。计划后续阶段默认包含这些修正，不允许绕过。

#### P0：安全和数据边界

1. 工作区、Diff、文件读取、文件保存和工作流接口直接接收客户端绝对路径。客户端可以尝试访问工作区根目录之外的文件，服务端也难以进行用户级授权。
2. `application-local.yml` 虽然被忽略，但本地文件中存在真实凭证形态的 API Key。该凭证必须立即在提供商侧撤销并重新生成；计划文档不记录具体值，后续只允许环境变量或外部 Secret 注入。
3. `SpringAiApiAdapter` 的 TLS/HTTP 实现需要重新审计。任何“信任所有证书”或绕过主机名验证的代码都不得进入生产路径。
4. 当前没有认证、用户、租户或资源授权模型；所有 REST 端点默认可访问，无法支持真实上线。
5. 预览与部署目前返回 HTML/Dockerfile 清单，尚未真正启动隔离沙箱或构建镜像。必须避免把“生成清单”描述为“已部署”。
6. H2 Console、默认数据库密码、开放 CORS、绝对 Windows 路径和默认会话兜底都只能存在于本地开发配置，不能出现在生产 profile。

#### P1：执行语义和领域模型

1. `IMCollaborationService` 同时负责消息落库、SSE 管理、@Mention 解析、任务拆解、Agent 调用和结果落库，接口浅且实现承担过多职责。需要拆为 Conversation、Orchestration、Execution、Event 适配器等深模块。
2. 当前使用 `CompletableFuture.runAsync` 默认线程池执行任务，任务没有持久化 Run/Step、队列、状态机、超时、重试、取消、恢复或幂等键，进程重启后无法恢复。
3. Orchestrator 逻辑硬编码 BackendArchitect、FrontendEngineer 的顺序调用，卡片与真实执行计划没有严格一一对应，也不能表达条件分支、并行、人工审批和失败补偿。
4. 所有会话和 Agent 运行都指向 `data/workspaces/default`，不符合多项目、多用户和并发协作要求；工作区锁还是单 JVM 内存锁。
5. Agent 的输出以大文本消息保存，缺少结构化事件、工具调用、文件变更、测试结果、token/cost、模型和 provider 信息，无法形成可审计运行记录。
6. SSE 连接只保存在内存，事件没有 sequence/id 和回放游标；客户端断线后无法可靠补齐事件。
7. 当前所谓 Spring AI 适配器包含自定义 HTTP/模拟逻辑，必须明确它是 `OpenAICompatibleChatPort` 还是 Spring AI 实现，避免简历和代码表述不一致。

#### P1：工程和产品体验

1. 前端 `page.tsx` 过大，API 调用、SSE、状态、视图和演示数据混在一个页面；工作区路径写死为 Windows 绝对路径，无法部署到 Linux 或容器。
2. 文件编辑器目前是 textarea，不具备真正的语法高亮、版本冲突检测、只读 Diff、保存版本和权限提示。
3. 工作流画布目前主要在前端构造固定节点，后端返回的执行状态不足以驱动画布状态；需要以后端 Workflow DSL 和 Run 状态为唯一事实来源。
4. 当前测试集中在 happy path，缺少真实 HTTP、并发写入、断线重连、路径穿越、未授权访问、CLI 超时、进程回收和数据库迁移测试。
5. CI 只做 Maven 测试和 Next 构建，没有数据库迁移校验、镜像构建、依赖/Secret 扫描、容器健康检查和最小端到端冒烟。

## 3. 参考项目的吸收边界

本仓库中的 `reference-shrimp-crab` 作为产品研究材料使用，不作为代码复制源。参考项目值得吸收的是产品对象和运行时思路：

| 参考能力 | AgentHub 的吸收方式 | 不直接照抄的部分 |
| --- | --- | --- |
| User、Agent Instance、Team、Project、Run、Run Step 等对象 | 用 Java/JPA 建立用户、AgentDefinition、AgentInstance、Team、Project、WorkflowRun、StepRun、Deliverable 模型 | 不复制其 Node/Express/Drizzle 实现 |
| Single Agent、Team Workflow、Project Workspace 三条产品路径 | 以“单聊试运行、团队编排、项目交付”作为同一执行模型的三种入口 | 不优先建设 Agent 社交市场 |
| Workflow DSL、条件节点、并行节点、取消和执行查询 | 设计版本化 JSON DSL + Java 校验器 + 持久化状态机 | 不把 DSL 当作前端临时 payload |
| Agent Runner 的 CLI 探测、跨 Windows/WSL/Linux 路径处理 | 将平台探测、命令解析、超时和进程树回收收敛到 Java Adapter/Runner 模块 | 不把 shell 拼接散落在业务服务中 |
| Runtime Context、技能和记忆文档 | 增加可控的上下文组装模块，并记录来源、版本和长度 | 不把工作区任意文件全部拼进 Prompt |
| Publish Safety、凭证扫描和文件过滤 | 建立交付前安全扫描、敏感信息检测、路径过滤和人工批准 | 不允许一键发布绕过检查 |
| HTTP/Workflow/Runtime smoke scripts | 形成 Testcontainers + 黑盒冒烟脚本和发布前证据 | 不以截图代替行为验证 |
| WebSocket 多端通信 | 第一阶段继续使用 SSE 简化单向事件流，确认回放与可靠性后再评估 WebSocket | 不为“像 IM”而过早引入双向协议复杂度 |

你的独特实现应集中在：Java 任务状态机、适配器契约、工作区并发治理、JGit 审计、Spring 生态可观测性和可测试的应用编排。

## 4. 目标架构

### 4.1 总体形态

采用 **模块化单体优先、可拆分部署** 的架构。第一版不拆微服务，以便个人项目控制复杂度；模块通过 Java interface/port 和领域事件通信，未来可以把 Execution Runtime、Event Gateway、Sandbox Worker 独立出来。

建议后端按业务模块组织，而不是继续按全局技术层堆放所有类：

```text
com.agenthub
├── shared                         # ID、时间、错误、分页、幂等、事件基础设施
├── identity                       # User、登录、Token、权限、审计主体
├── project                        # Project、Workspace、Artifact、文件边界
├── agent                          # AgentDefinition、Instance、Capability、Provider
├── conversation                   # Conversation、Message、Mention、SSE 订阅
├── orchestration                  # Plan、WorkflowDefinition、TaskGraph、审批
├── execution                      # WorkflowRun、StepRun、队列、重试、取消、恢复
├── runtime                        # UnifiedAgentAdapter、CLI/API/Mock Adapter、工具
├── audit                          # Git Snapshot、Diff、TestReport、Evidence
├── sandbox                        # Preview、Build、Deploy、DeploymentRecord
└── infrastructure                 # JPA、Flyway、Redis、进程、文件系统、外部 API
```

每个模块内部继续使用 `api/application/domain/infrastructure` 或 ports-and-adapters 结构。模块外只暴露少量深接口，例如：

- `ConversationApplication.sendMessage(command)`；
- `PlanApplication.createPlan(command)`；
- `ExecutionApplication.startRun(command)`、`cancelRun(id)`、`retryStep(id)`；
- `AgentRuntime.execute(request, eventSink)`；
- `WorkspaceApplication.readTree(projectId)`、`computeDiff(runId)`；
- `DeliveryApplication.validateAndDeploy(projectId, version, approval)`。

调用方不应知道 CLI 命令格式、数据库实体关系、JGit iterator 或进程回收细节。

### 4.2 核心领域对象

统一以下术语，后续代码、接口和简历都使用这些名字：

- **User**：平台登录主体。
- **Project**：用户希望持续推进的研发项目，拥有一个或多个 Workspace。
- **Workspace**：某个 Project 的受控文件目录和 Git 仓库；不接受客户端传入绝对路径作为身份。
- **AgentDefinition**：平台注册的 Agent 能力定义，包括角色、系统提示词、能力和允许工具。
- **AgentInstance**：某用户或项目实际使用的 Agent 配置，包括 provider、模型、运行时和权限。
- **Team**：一组 AgentInstance 与角色/连接关系。
- **Plan**：Orchestrator 针对用户目标生成的任务计划，包含版本化 WorkflowDefinition。
- **WorkflowRun**：一次计划执行的实例，有 queued/running/paused/succeeded/failed/cancelled 状态。
- **StepRun**：WorkflowRun 中一个节点的执行实例，有输入快照、输出摘要、日志、重试次数和产物引用。
- **Artifact**：执行产生的文件、Diff、测试报告、日志、预览或部署清单。
- **Approval**：需要人确认的节点或交付动作。
- **Event**：可持久化、可排序、可回放的领域/运行时事件。

边界规则：Message 是用户交互记录，StepRun 是执行记录，Artifact 是产物记录，三者不能互相替代。

### 4.3 执行状态机

WorkflowRun：

```text
QUEUED -> RUNNING -> PAUSED -> RUNNING
QUEUED/RUNNING/PAUSED -> CANCELLING -> CANCELLED
RUNNING -> SUCCEEDED
RUNNING -> FAILED -> RETRYING -> RUNNING
```

StepRun：

```text
PENDING -> READY -> RUNNING -> WAITING_APPROVAL -> SUCCEEDED
READY/RUNNING -> RETRYING -> READY
READY/RUNNING -> FAILED
PENDING/READY/RUNNING -> CANCELLED
```

状态转换只能通过一个状态机模块完成，不能由 Controller、Adapter 或前端直接修改数据库状态。所有转换要记录 actor、原因、时间和 correlationId。

### 4.4 运行时接口

目标接口要比当前 `execute/executeStream` 更能表达工程事实，但仍保持小而深：

```java
public interface AgentRuntime {
    RuntimeDescriptor describe();
    RuntimeHealth checkHealth();
    ExecutionHandle start(AgentExecutionRequest request, RuntimeEventSink sink);
    void cancel(ExecutionHandle handle);
}
```

`AgentExecutionRequest` 必须包含 runId、stepRunId、agentInstanceId、workspaceId、sessionId、prompt/context、工具白名单、超时、资源限制和幂等键。`RuntimeEventSink` 统一接收 token、assistant message、tool call、file change、log、usage、completed、failed 等事件。

适配器至少分为：

- `MockAgentRuntime`：离线演示和测试，明确标记 `simulated=true`；
- `OpenAiCompatibleRuntime`：DeepSeek 等 OpenAI-compatible API；
- `CodexCliRuntime`：Codex CLI；
- `ClaudeCodeRuntime`：Claude Code CLI；
- `OpenClawRuntime`：OpenClaw CLI；
- `SpringAiRuntime`：只有真正使用 Spring AI 客户端时才保留此命名。

所有适配器共享超时、取消、输出脱敏、进程树回收、重试策略和健康检查，不各自重复实现。

### 4.5 持久化模型最小集合

使用 Flyway 管理迁移。开发环境可用 H2，集成和生产使用 MySQL 8；禁止以 `ddl-auto=update` 作为生产迁移方案。

第一版至少包含：

| 表 | 关键字段 | 目的 |
| --- | --- | --- |
| `users` | id、email、password_hash、status、created_at | 登录和资源归属 |
| `projects` | id、owner_id、name、status、workspace_id | 项目边界 |
| `workspaces` | id、project_id、relative_root、git_baseline_commit | 受控工作区，不暴露绝对路径 |
| `agent_definitions` | id、key、name、role、manifest、version | 可复用 Agent 能力 |
| `agent_instances` | id、owner_id/project_id、definition_id、runtime_type、provider_id、status | 具体 Agent 配置 |
| `providers` | id、owner_id、type、base_url、secret_ref、model、status | 模型/CLI Provider 元数据，密钥不明文存库 |
| `teams` / `team_members` | team_id、agent_instance_id、role、sort_order | 团队编排 |
| `conversations` / `messages` | conversation_id、sender、mentions、sequence、payload | IM 与执行入口 |
| `workflow_definitions` | id、version、schema_version、dsl_json、checksum | 可复现计划 |
| `workflow_runs` | id、project_id、definition_id、status、idempotency_key、timestamps | 执行实例 |
| `step_runs` | run_id、node_id、status、attempt、input_ref、output_ref、error | 节点执行与恢复 |
| `run_events` | run_id、sequence、event_type、payload、created_at | SSE 回放和审计 |
| `artifacts` | run_id/step_run_id、type、path/ref、checksum、metadata | 文件、Diff、测试、部署物 |
| `approvals` | run_id/step_run_id、status、requested_by、reviewed_by | 人工门禁 |
| `deployments` | project_id、artifact_id、status、target、url、started_at | 真实部署记录 |

消息和事件列表必须按 `(conversation_id, sequence)` 或 `(run_id, sequence)` 建索引；所有外键、唯一键、状态字段和幂等键都要在迁移中显式定义。

## 5. 阶段路线图

时间是估算值，阶段退出条件比日历日期优先。除非某阶段的退出条件满足，否则不要并行堆新功能。

### 阶段 0：止血、冻结范围和基线（预计 2～3 天）

**目标**：把当前演示代码变成安全、可复现、可继续演进的基线。

**具体工作**：

1. 撤销当前本地及任何可能暴露过的模型 API Key，重新生成凭证；检查 Git 历史、截图、日志和构建产物，确认没有密钥。
2. 建立 `dev/test/prod` 配置边界：生产关闭 H2 Console、关闭 `ddl-auto=update`、禁止默认密码和 localhost/Windows 绝对路径。
3. 为所有工作区操作引入 `workspaceId/projectId`，暂时保留旧路径接口但标记 deprecated，内部统一解析到配置的 workspace root。
4. 写出 ADR-001（模块化单体）、ADR-002（SSE + 持久化事件）、ADR-003（Workspace 不信任客户端绝对路径）、ADR-004（Mock Runtime 仅测试/演示）。
5. 把 README 中“已部署”“生产级沙箱”等表述改为与事实一致的“生成清单/本地预览”，直到后续阶段真正完成。
6. 确认 Git working tree clean，记录当前 Maven/Next 构建命令、Java/Node 版本和一条可重复的演示脚本。

**退出条件**：

- Secret 扫描无真实凭证；
- 任何文件接口无法读取 workspace root 之外的路径；
- `application-prod.yml` 不含开发默认值；
- 新人按 README 可在干净环境完成启动和基础测试；
- 形成一页《当前真实能力边界》文档。

### 阶段 1：领域重构与持久化地基（预计 1～2 周）

**目标**：从“演示服务”升级为可恢复的 Java 模块化单体。

**具体工作**：

1. 按 `identity/project/agent/conversation/orchestration/execution/runtime/audit/sandbox` 建立模块包；Controller 只调用 Application 接口。
2. 引入 Flyway 和 MySQL 测试容器，完成用户、项目、工作区、Agent、Provider、会话、消息、WorkflowRun、StepRun、Event、Artifact、Approval、Deployment 迁移。
3. 将 JPA Entity 与领域对象分离，避免 Controller 直接返回 Entity；定义 command、query、view DTO。
4. 增加用户注册/登录/刷新 Token/退出接口和资源归属检查。单用户本地模式也必须经过同一授权接口，不能用默认 user-1 兜底。
5. 把 `ConversationEntity.agentIds` 这种逗号字符串改为关系表；把 `MessageEntity.cardPayloadJson` 等 JSON 字段定义 schema version。
6. 建立统一错误码、requestId/correlationId、分页、时间和 ID 生成策略。
7. 关闭 `spring.jpa.open-in-view`，为事务边界和查询边界补充测试。

**退出条件**：

- 干净数据库可由迁移创建，迁移可重复执行并可回滚到上一版本；
- 用户只能读取和修改自己的 Project/Conversation/Workspace；
- Maven 单元测试、Repository 测试、Testcontainers 集成测试全部通过；
- 应用重启后会话、消息、Run、事件仍可查询；
- 包依赖方向通过 ArchUnit 或等价检查。

### 阶段 2：安全工作区与 JGit 审计升级（✅ 已圆满交付）

> **阶段交付状态**：已完成全部开发、边界防护测试与架构守护验证（后端 73/73 全绿灯，前端 npm run build 通过）。

**目标**：让代码生成行为具备可控的文件边界、快照、审查和回滚能力。

**具体工作**：

1. `WorkspaceResolver` 只接受 workspaceId + relativePath，使用 `Path.normalize()`、`toRealPath()`、符号链接检查和 root containment 检查。
2. 统一文件树、文件读取、文件写入、重命名、下载、归档接口；拒绝 `.git` 内部写入、设备文件、超大文件和二进制预览。
3. 每次 Run 开始建立 baseline commit；每个重要 Step 完成建立 snapshot/tag，记录 commit、checksum 和生成者。
4. JGit 模块输出结构化 Diff：文件状态、行数、patch、binary、重命名、冲突和审查状态。
5. 增加并发模型：单 JVM 使用 keyed lock；多实例使用 Redis/数据库租约；锁必须带 owner、租期、续期和超时恢复。
6. 支持用户对 Step 产物执行 accept/reject/revert；回滚只回滚受控 snapshot，不允许直接重置用户未提交内容。

**退出条件与验证证据**：

- [x] 路径穿越、绝对路径、符号链接和 `.git` 写入测试全部拒绝（3003, 3004, 3005 拦截校验通过）；
- [x] 两个并发 Run 不能同时修改同一 Workspace（多 owner 互斥与租约防死锁测试通过）；
- [x] Diff 可从 Run/Step 页面稳定重现（`StructuredDiff` 引擎已输出，含 Unified Patch 与二进制/重命名/冲突检测）；
- [x] 回滚后文件、Git 状态和数据库 Artifact 状态一致（安全回滚测试已确认恢复 baseline 且未破坏无关文件）。

### 阶段 3：Agent Runtime 与 Provider 适配层（预计 1～2 周）

**目标**：把当前“能调用”升级为可观测、可取消、可替换的 Agent 运行时。

**具体工作**：

1. 将 `UnifiedAgentAdapter` 演进为 `AgentRuntime` + `RuntimeEventSink`，定义统一事件类型和错误分类。
2. 先实现 `MockAgentRuntime`，再实现 OpenAI-compatible API、Codex CLI、Claude Code CLI；OpenClaw 作为兼容适配器，不阻塞主链路。
3. 统一命令解析和跨平台路径：Windows 原生、WSL、Linux 容器分别测试；严禁拼接未经转义的 shell 字符串。
4. CLI 进程加入超时、取消、进程树终止、stdout/stderr 分流、输出上限、敏感信息脱敏和退出码映射。
5. Provider 密钥仅从 Secret Manager/环境变量读取；数据库只保存 secretRef、provider 类型、模型和健康状态。
6. 记录 model、provider、prompt version、input/output token、估算成本、duration、simulated、runtime version。
7. 设计上下文组装器：项目说明、工作区规则、Agent 角色、任务输入、必要文件摘要和历史摘要分层注入，并记录 context manifest。
8. 为每个 Adapter 增加 contract test：健康检查、成功、超时、取消、失败、流式事件顺序、敏感信息脱敏。

**退出条件**：

- 一个 AgentRuntime 可被另一个实现替换而不修改 Application 层；
- 真实 API/CLI 不可用时只在明确标记 degraded/simulated 的模式下降级；
- 超时和取消能在限定时间内回收进程并落库；
- 运行日志不出现 API Key、Authorization、密码或完整 Prompt 中的敏感字段；
- Adapter contract test 和至少一条真实 CLI 手工冒烟通过。

### 阶段 4：Orchestrator 与可靠执行引擎（预计 2～3 周）

**目标**：把静态 DAG 演示变成可恢复的任务执行产品，这是简历项目的核心阶段。

**具体工作**：

1. 定义版本化 Workflow DSL：`schemaVersion`、entryNodeId、nodes、edges、node type、agentRef、input mapping、retry、timeout、approval、condition、parallel policy。
2. 编写 DSL 校验器：节点 ID 唯一、入口存在、图无非法环、条件分支完整、引用的 Agent/工具存在、超时和重试范围合法。
3. Orchestrator 只负责目标理解、计划生成、计划解释和计划版本；Executor 负责按照已确认计划执行，避免“边想边改图”。
4. 引入 `TaskPlanCreated`、`RunQueued`、`StepReady`、`StepStarted`、`AgentEventReceived`、`StepSucceeded`、`ApprovalRequested`、`RunFailed` 等事件。
5. 使用 Spring TaskExecutor + 持久化队列实现单机版本；定义未来切换 Redis Streams/RabbitMQ 的 Port。不得直接使用默认 `CompletableFuture` 公共线程池作为核心队列。
6. 实现 DAG 调度：可并行节点等待依赖完成，条件节点只激活一条分支，失败节点按策略重试或中止，人工审批节点暂停 Run。
7. 实现查询、取消、暂停、恢复、重试单 Step、从某个 Step 重新执行和幂等启动。
8. 为每个 Step 保存输入快照、上下文 manifest、输出摘要、Artifact 引用和错误分类；大文本进入对象存储/文件存储，数据库只存引用和摘要。
9. 将前端画布改为 DSL 编辑器的投影：后端返回节点状态，前端只负责编辑草稿和展示执行状态。

**退出条件**：

- 一个“后端设计 -> 前端实现 -> QA 审查 -> 人工批准”的 Run 可以完整执行；
- 中途杀死应用后，重启能从最后一个可恢复 Step 继续；
- 单个 Step 超时、失败、重试、取消和人工暂停都有可查询证据；
- 同一幂等键重复提交不会创建两个 Run；
- 条件分支和并行节点有自动化测试；
- SSE 断线重连可使用 `Last-Event-ID` 或 sequence 游标补齐事件。

### 阶段 5：IM、事件流和交互闭环（预计 1～2 周）

**目标**：让聊天体验成为可靠的执行入口，而不是一个隐藏的后台副作用。

**具体工作**：

1. 会话创建必须绑定 User、Project 和参与者；单聊、群聊、执行会话使用明确 ConversationType。
2. @Mention 解析成结构化 Mention，校验 Agent 是否属于当前 Team/Project；禁止根据任意字符串直接选择平台类型。
3. 用户消息先落库，再创建 Command/Plan/Run；消息发送接口返回 messageId/runId，避免请求一直等待 Agent 完成。
4. SSE 统一推送连接、消息、token、运行状态、节点状态、日志、Diff、审批和心跳事件；每类事件有 schema version。
5. 事件广播与持久化解耦：先写 `run_events`，再由 EventPublisher 推送；客户端可按 conversation/run 查询历史并回放。
6. 对长文本做摘要和分页；提供“查看完整 Prompt/日志/差异”的权限受控接口。
7. 前端拆成 Chat、RunTimeline、ApprovalCard、EventStream、ConversationList、ProjectContext 模块；统一 API client 和错误状态。

**退出条件**：

- 用户可以从新会话发起 Run，实时看到拆解、节点、token、产物和最终结果；
- 刷新页面或断网重连后，消息和运行时间线不丢失；
- 未授权用户无法订阅别人的 Conversation/Run；
- 连接数、事件速率和慢消费者有上限与可观测指标。

### 阶段 6：交付物、测试门禁和人工审批（预计 1～2 周）

**目标**：把 Agent 输出变成可以判断、审查和交付的工程成果。

**具体工作**：

1. 定义 Artifact 类型：source change、diff、test report、build log、preview、deployment manifest、runtime log。
2. QA Agent 不只输出自然语言，要调用受限工具执行格式化、编译、单元测试、静态检查和安全扫描，并把结果结构化。
3. 实现质量门禁：编译失败、测试失败、Secret 扫描命中、Diff 超预算或路径违规时自动阻止发布。
4. 审批卡片展示风险摘要、变更文件、Diff、测试结果和发布目标；审批记录 reviewer、时间、意见和策略版本。
5. 为 Java 后端加入 Checkstyle/SpotBugs/依赖漏洞扫描；前端加入 lint/typecheck/build；必要时加入 OWASP/Secret 扫描。
6. 生成一次 Run 的可分享证据包：计划、事件、Prompt 版本、Agent 版本、Diff、测试、审批和产物 checksum。

**退出条件**：

- 用户可以逐步接受或拒绝某个 Step 的产物；
- 失败门禁能阻断部署，并说明原因；
- 一键导出证据包后第三方无需访问数据库即可理解一次 Run；
- 质量门禁结果在 UI、API 和日志中的状态一致。

### 阶段 7：真实预览与部署（预计 1～2 周）

**目标**：将当前“返回 Dockerfile 字符串”升级为安全、可回收的本地/测试环境部署。

**具体工作**：

1. 先支持静态前端项目：构建命令、产物目录、端口、启动命令和环境变量必须来自受校验的 DeploymentSpec。
2. 预览采用短生命周期容器或独立 worker；不在 Spring Boot 主进程内执行不受控的 npm、Docker 或 shell 命令。
3. 容器使用非 root 用户、只读根文件系统、CPU/内存/磁盘/网络限制、超时和自动清理；预览域名/端口与 Project/Deployment 记录绑定。
4. 部署动作必须经过 Artifact 审批和质量门禁；运行中记录 build、start、health check、stop、cleanup 状态。
5. 第一版只实现本地 Docker Compose/单机目标；Kubernetes 只输出经验证的模板，不宣称已接入集群。
6. 增加部署回滚、日志查看、健康检查、过期回收和失败清理。

**退出条件**：

- 从一次通过审批的 Run 产生真实可访问预览；
- 构建失败、启动失败、健康检查失败均可查询和回收；
- 一个 Project 的容器不能读取另一个 Project 的文件；
- 部署记录、URL、Artifact checksum 和实际容器状态一致。

### 阶段 8：上线工程和运维验证（预计 1～2 周）

**目标**：让系统可以在一台 Linux 服务器上稳定运行，并且出问题时可诊断。

**具体工作**：

1. 完善生产 Dockerfile、Compose、反向代理、TLS、健康检查、资源限制、日志轮转和数据卷备份。
2. MySQL 使用显式迁移；Redis 仅在需要分布式锁/队列/事件扩展时启用；所有服务使用独立非 root 用户。
3. 引入 Spring Boot Actuator、Micrometer 指标、结构化日志和 OpenTelemetry trace。核心指标包括 Run 成功率、Step 延迟、队列长度、Agent 错误、token/cost、SSE 连接数、工作区锁等待和部署失败率。
4. 配置告警：任务长期 queued、失败率升高、CLI 僵尸进程、磁盘接近上限、事件发布失败、数据库连接池耗尽。
5. CI/CD 增加：后端测试、前端构建、迁移校验、容器构建、依赖漏洞扫描、Secret 扫描、黑盒冒烟、健康检查。
6. 做一次故障演练：数据库重启、Agent 超时、进程强杀、SSE 断线、磁盘不足、Redis 不可用、部署失败，并记录恢复步骤。

**退出条件**：

- 新服务器从 `.env`/Secret 模板可部署，不依赖开发机绝对路径；
- 健康检查、日志、指标和告警可用；
- 关键故障有明确恢复手册；
- 连续运行 24 小时的冒烟任务无资源泄漏和未回收进程。

### 阶段 9：作品化、答辩和简历证据（预计 3～5 天）

**目标**：让项目成果能够被面试官快速理解、验证和追问。

**具体工作**：

1. README 首屏只保留产品定位、架构图、核心演示、快速启动和真实能力边界。
2. 准备一条 5～8 分钟演示：创建项目 -> 选择 Team -> 输入需求 -> Orchestrator 生成计划 -> 并行执行 -> SSE 时间线 -> JGit Diff -> 测试门禁 -> 审批 -> 预览/部署 -> 导出证据包。
3. 准备架构图、状态机图、事件流图、数据库 ER 图和一次真实 Run 的脱敏证据包。
4. 每项简历数据必须来自真实记录，例如“支持 N 个 Adapter”“Run 可恢复率”“Step 重试次数”“测试数量”“平均延迟”。没有测量就不要写百分比。
5. 写出 3 个可追问的技术决策：为什么模块化单体、为什么 SSE + 持久化事件、为什么工作区采用 JGit snapshot + 受控路径。
6. 记录局限：当前支持的 Provider、并发规模、部署目标、未实现的 WebSocket/Kubernetes/多租户能力。

**退出条件**：

- 干净环境按文档能完成演示；
- 演示中的每个“成功”都有 API、日志、数据库或产物证据；
- 面试官可以从 README 进入代码并定位到核心模块和测试；
- 不把模拟器结果、静态截图或 Dockerfile 文本包装成真实生产能力。

## 6. 端到端验收场景

所有阶段最终都围绕以下场景验收，避免功能各自完成但主链路断裂。

### 场景 A：单 Agent 试运行

用户登录后创建 Project 和 Workspace，选择一个 AgentInstance，发送“为项目增加登录接口”。平台创建 Conversation 和 Run，Runtime 输出流式事件，代码写入该 Project 的 workspace，生成 Diff 和测试报告。用户可查看、拒绝或接受结果。

### 场景 B：多 Agent 团队协作

用户发送“为项目增加登录接口并提供前端页面”。Orchestrator 生成版本化计划：Backend、Frontend 并行，QA 等待两者完成，Approval 等待 QA 通过。计划被保存后执行，任意节点失败可以重试，应用重启后可以恢复，前端时间线显示每个 Step 的状态和产物。

### 场景 C：不可信输入和安全边界

用户或 Agent 试图读取 `../`、符号链接、`.git/config`、另一个 Project 的路径或写入工作区之外的文件，系统拒绝并记录安全事件。Prompt 中包含疑似密钥时，日志和事件中只显示脱敏值。

### 场景 D：断线、取消和恢复

执行过程中关闭浏览器、断开 SSE、发送取消请求并重启后端。Run 状态必须保持一致，客户端重新连接可按 sequence 补齐事件；已完成 Step 不重复执行，正在运行的进程被回收，未执行节点保持可恢复状态。

### 场景 E：交付和回滚

QA 门禁通过后，用户审查 Diff 和测试报告并批准预览。构建或健康检查失败时，系统清理资源并保留失败证据；用户可以回滚到上一个已批准 snapshot。

## 7. API 演进原则

保留 `/api` 前缀，但从“按页面拼端点”演进为资源和命令清晰的接口：

```text
POST   /api/auth/register
POST   /api/auth/login
GET    /api/projects
POST   /api/projects
GET    /api/projects/{projectId}
GET    /api/projects/{projectId}/workspace/tree
GET    /api/projects/{projectId}/workspace/files/content?path=...
POST   /api/projects/{projectId}/workspace/files
GET    /api/agents/definitions
POST   /api/agent-instances
POST   /api/conversations
POST   /api/conversations/{conversationId}/messages
GET    /api/conversations/{conversationId}/events?after=...
GET    /api/conversations/{conversationId}/stream
POST   /api/plans
POST   /api/runs
GET    /api/runs/{runId}
POST   /api/runs/{runId}/cancel
POST   /api/step-runs/{stepRunId}/retry
POST   /api/approvals/{approvalId}/decision
GET    /api/runs/{runId}/artifacts
GET    /api/runs/{runId}/diff
POST   /api/deployments
GET    /api/deployments/{deploymentId}
```

所有写接口需要：认证主体、幂等键（适用时）、版本/ETag（适用时）、统一错误码和 correlationId。接口返回 DTO，不返回 JPA Entity；异步启动返回 `202 Accepted + runId`，不等待整个 Agent 任务。

## 8. 测试策略

测试按风险分层，不追求无意义的覆盖率数字。

| 层级 | 重点 | 必须覆盖 |
| --- | --- | --- |
| 领域单测 | 纯规则和状态机 | Mention、DSL 校验、状态迁移、重试、条件分支、权限策略 |
| Adapter Contract | 运行时契约 | 成功、流式顺序、超时、取消、退出码、脱敏、降级 |
| Repository/迁移 | 数据一致性 | 外键、唯一键、迁移、分页、事件 sequence、重启恢复 |
| Spring MVC 集成 | 接口边界 | 鉴权、DTO、错误码、SSE、幂等、未授权、路径攻击 |
| Testcontainers | 真实依赖 | MySQL、Redis（启用时）、文件存储、容器网络 |
| 黑盒冒烟 | 业务主链路 | 登录 -> 项目 -> Run -> Diff -> 审批 -> 预览 |
| 压测/故障 | 运行稳定性 | 并发 Run、SSE 连接、队列积压、Agent 超时、资源回收 |
| 前端回归 | 用户体验 | 登录、会话、断线重连、画布状态、Diff、审批、错误展示 |

每个新阶段必须补一条能证明用户价值的测试；测试不得只断言“返回 200”而不验证数据库状态、事件顺序、文件边界和产物。

## 9. 不漂移规则

后续实现遇到新想法时，遵守以下规则：

1. 任何新功能先回答它是否服务“需求到可审计交付”主链路；不能回答就进入候选清单，不插入当前阶段。
2. 任何 Agent 能力先定义输入、输出、工具权限、失败语义和 Artifact，再写 Prompt 或 UI。
3. 任何异步动作必须有 Run/Step 身份、幂等键、状态、超时、取消和事件；禁止再次把 `CompletableFuture.runAsync` 直接放进 Controller/会话服务。
4. 任何文件操作必须通过 Workspace Port；禁止 Controller、前端或 Adapter 自行拼绝对路径。
5. 任何“已完成/已部署/生产级”表述必须能由运行记录、健康检查或产物证明；模拟模式必须显式显示。
6. 任何新 Adapter 至少提供 contract test、健康检查、超时和脱敏实现。
7. 任何数据库字段变化先写迁移和领域含义，再改 Entity；禁止依赖 `ddl-auto=update` 隐式变更。
8. 优先加深已有模块接口，避免为每个页面再加一个浅薄 Service/Controller。
9. 先完成当前阶段退出条件，再扩充市场、社交、移动端、桌面端或多租户。
10. 每周结束更新本计划中的状态、证据链接、风险和下一阶段入口；只更新事实，不把愿望写成完成。

## 10. 推荐的第一批实施顺序

如果马上开始实施，严格按以下顺序：

1. 撤销并重建本地模型 API Key，完成 Secret 历史扫描。
2. 修正配置 profile、关闭生产 H2 Console 和默认密码。
3. 引入 `WorkspaceResolver`，移除所有客户端绝对路径依赖。
4. 建立 User/Project/Workspace/Run/Step/Event 数据模型与 Flyway 迁移。
5. 将 `IMCollaborationService` 拆为消息应用服务、计划应用服务和运行应用服务。
6. 以 Mock Runtime 写出可回放的 Run/Step/SSE 端到端测试。
7. 将现有 DeepSeek/CLI 逻辑迁移到新的 AgentRuntime Port，并补 contract test。
8. 实现可持久化的 Workflow DSL、状态机、重试、取消和恢复。
9. 把前端固定演示数据改成 Project/Run/Event API 驱动。
10. 最后再接真实容器预览、部署和上线运维。

这十步完成前，不应继续增加社交、市场、移动端或更多 Agent 平台数量。

## 11. 最终交付清单

上线前必须存在以下可检查产物：

- `README.md`：定位、能力边界、架构图、启动、演示和故障排查；
- `docs/ARCHITECTURE.md`：模块、数据流、状态机、事件模型和安全边界；
- `docs/API.md`：版本化接口、SSE 事件 schema、错误码和幂等规则；
- `docs/adr/`：关键不可逆决策；
- Flyway migration：从空库可创建完整生产 schema；
- `docker-compose.prod.yml` 或等价部署清单；
- CI 报告：测试、构建、迁移、扫描、镜像和冒烟结果；
- 脱敏 Run evidence bundle：计划、事件、Diff、测试、审批和部署记录；
- 回滚与故障演练手册；
- 一条不依赖开发机绝对路径的端到端演示脚本。

当这些产物齐备时，AgentHub 才可以被描述为“可落地、可上线的 Java 多 Agent 协作平台”；在此之前，应准确描述为“具备 IM/DAG/JGit/Adapter 演示骨架的持续演进项目”。
