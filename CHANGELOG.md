# 更新记录 (Changelog)

所有关键架构升级、特性新增与重要缺陷修复均按版本记录于此。

---

## [v1.5.0] - 2026-10-08

### 🌟 阶段 5：Agent 统一接入层、多 Provider 与动态路由 (Phase 5 Deliverables)
- **Provider SPI 抽象与统一交互契约 (`LlmProvider` & Standard SPI)**：
  - 标准化统一定义统一消息模型 `ChatMessage` (role: system/user/assistant/tool, content, name)、请求模型 `ChatRequest` 与响应模型 `ChatResponse`；
  - 规范流式 Chunk 契约 `ChatChunk`，统一打通同步与 SSE 流式事件推送；
  - 细粒度测量每次调用的真实物理延迟（`latencyMs`）并准确统计 Token 消耗 (`prompt_tokens`, `completion_tokens`, `total_tokens`)；
- **多 Provider 生态适配矩阵 (Multi-Provider Ecosystem)**：
  - 落地 `OpenAiCompatibleProvider`（兼容 OpenAI 与 DeepSeek 标准 chat completions 协议）；
  - 落地 `AnthropicProvider`（Claude messages 协议，处理分层 system/messages 与 content_block_delta 流式协议）；
  - 落地 `GeminiProvider`（Google Gemini generateContent 与 streamGenerateContent REST 协议）；
  - 落地 `OllamaProvider`（本地 Ollama / vLLM 原生 `/api/chat` 零成本执行）；
  - 落地 `MockLlmProvider`（确定性模拟故障注入，支持 429 限流、5xx 服务端异常与延迟仿真）；
- **全链路凭证安全与敏感信息脱敏 (`SecretMasker`)**：
  - 支持 `env:VAR_NAME` 环境变量注入与 `prop:KEY` 配置解耦；
  - 严格防御全链路凭证泄漏：API Key 自动脱敏为 `sk-***` / `[REDACTED_SECRET]`，全面清洗日志、异常堆栈、请求头（`Authorization`, `x-api-key`）及 URL 查询参数（Gemini `?key=...`）；REST 接口与 DTO 绝不明文返回敏感密钥；
- **动态路由策略与高可用智能选择器 (`DynamicProviderRouter`)**：
  - 支持多维动态加权路由选择：按优先级 (`priority` 降序)、模型能力需求 (`capabilities`: code/general/fast/reasoning/long_context)、健康延迟 (`latencyMs` 升序) 与权重 (`weight`) 智能挑选最合适 Provider；
  - 提供路由决策预演端点 `POST /api/providers/route`，清晰返回主候选节点与备用节点链条；
- **高可用容灾与自动 Fallback 降级 (HA Failover & Circuit Breaking)**：
  - 落地线程安全熔断器 `CircuitBreaker`，管理 `CLOSED`, `OPEN`, `HALF_OPEN` 状态机流转；
  - 当主 Provider 发生 429 限流、5xx 超时、网络中断或连续失败达到阈值时，自动触发熔断并快速切换至备用 Backup Provider；
  - 自动向事件流和运行日志沉淀标准化降级告警事件 `FallbackEvent`（包含失败原因、状态码与接管节点）；
- **Token 与成本计量治理 (Token & Cost Accounting)**：
  - 落地 `ModelPricing` 定价核算引擎，内置主流大模型官方基准牌价（GPT-4o, DeepSeek, Claude 3.5 Sonnet, Gemini 1.5 Flash, Ollama 本地零成本），同时支持 Provider 实体自定义输入/输出百万 Token 计费覆盖；
  - 每次 LLM 调用精细化落库 `token_usages` 审计表，提供平台级总 Token 消耗与成本计量汇总端点；
- **Flyway 数据库演进 (`V6__phase5_agent_providers_and_routing.sql`)**：
  - 升级 `providers` 表（增加 priority, weight, capabilities, cost_per_million_input, cost_per_million_output, circuit_status, avg_latency_ms 等）；
  - 升级 `agent_definitions` 表（增加 preferred_provider_type, preferred_model, required_capabilities, fallback_enabled 等）；
  - 新增 `token_usages` 审计表并建立 `idx_token_usages_run`, `idx_token_usages_provider`, `idx_token_usages_created`, `idx_providers_status_priority` 高效索引；
  - 初始化系统级四大主流 Provider 及本地 Ollama 种子基线数据；
- **集成内核与 REST 端点**：
  - 落地 `ProviderDrivenAgentRuntime`，与 `ExecutionScheduler`、`DagExecutionEngine` 和 `AgentRuntimeRegistry` 深度集成；
  - 新增 REST 控制器 `ProviderController` (`/api/providers`) 与 `TokenUsageController` (`/api/token-usages`)；
- **全绿灯测试矩阵与运行时加固**：
  - 新增 7 大测试套件：`ProviderSpiAndPolymorphismContractTest`、`DynamicProviderRouterAndRoutingPolicyTest`、`ProviderFaultToleranceAndFallbackTest`、`TokenCostAccountingAndAuditingTest`、`SecretMaskingAndCredentialSecurityTest`、`ProviderAndTokenUsageControllerIntegrationTest` 与端到端 `ProviderDrivenAgentRuntimeIntegrationTest`；
  - 加固 `CircuitBreaker` 状态机：支持 `HALF_OPEN` 单探针并发隔离与失败即刻回退、真实物理延迟原子捕获与动态加权路由打分；
  - 加固多 Provider 消息模型防御：防范 null role 与 null content，杜绝 NPE；
  - 加固凭证安全：`SecretMasker` 扩展清洗 `x-api-key` 与 `x-goog-api-key` 请求头，Google Gemini 迁移至 Header 传参消灭 URL 泄密隐患；
  - 加固运行时外键约束：`TokenUsageApplicationService` 与 `ProviderDrivenAgentRuntime` 校验真实 providerId，彻底杜绝外键约束破损；
  - 更新 `FlywayMigrationAndSchemaTest` 验证 19 张核心领域表与 V6 脚本；
  - 后端 163/163 项测试 100% 绿灯全通；前端 Next.js 14 生产构建 100% 成功。

---

## [v1.4.0] - 2026-10-08


### 🌟 阶段 4：工作流编排引擎、DAG 依赖与数据流拓扑 (Phase 4 Deliverables)
- **版本化工作流 DSL 与 Kahn 拓扑排序校验 (`WorkflowDslValidator`)**：
  - 规范化定义 `WorkflowDsl`, `WorkflowNodeDsl`, `WorkflowEdgeDsl`, `RetryPolicyDsl` 等核心模型；
  - 基于 Kahn 算法实现确定性有向无环图 (DAG) 拓扑排序分层与成环检测（Cycle Detection），非法成环与自环检测严格抛出专用业务异常 `6001 WORKFLOW_INVALID`；
  - 覆盖孤岛节点孤立检测与入口节点/出口节点合法性严格校验；
- **真实并发调度与有界线程池 DAG 执行引擎 (`DagExecutionEngine`)**：
  - 调度引擎基于有界受控线程池调度兄弟节点真实并发执行，支持 `all_succeeded`、`any_succeeded` 以及自定义前置条件动态收敛；
  - 严格协同阶段 3 的底层状态机 `ExecutionStateMachine`，保障节点间状态转换原子性与细粒度锁安全性；
  - 优化全局快速响应中止与取消感知机制（`abortRemainingNodes` 与 100ms 快速中断轮询），彻底消除依赖中止与人工拒绝场景下的线程挂起与超时悬挂问题；
- **安全数据流管道与零 RCE 风险表达式计算 (`WorkflowExecutionContext` & `SafeExpressionEvaluator`)**：
  - 实现了线程安全的工作流上下文数据管线，支持动态参数插值与跨节点上下文字段提取（`{{steps.<nodeId>.outputs.<key>}}` 与 `{{inputs.<key>}}`）；
  - 彻底摈弃危险的 SpEL / 反射执行机制，自研递归下降安全表达式求值引擎，支持安全比较（`==`, `!=`, `>`, `<`, `>=`, `<=`）、逻辑组合（`&&`, `||`, `!`）与空安全解析，从根源杜绝远程代码执行 (RCE) 漏洞；
- **动态条件分支与级联跳过剪枝 (Dynamic Branching & Cascade Skip Pruning)**：
  - 智能评估边上的 `condition` 表达式，未命中分支对应的节点状态优雅收敛为 `SKIPPED`；
  - 下游依赖于未执行前置节点的后续分支自动触发级联跳过剪枝，避免任务悬挂与脏执行；
- **人工审批门禁 (Human-in-the-Loop) 与中断/恢复机制 (`ApprovalApplication`)**：
  - 支持 `requiresApproval: true` 的关键质量门禁与高危操作节点，执行至此时挂起当前 Step 与 Run 为 `WAITING_APPROVAL`；
  - 提供标准 REST 接口（`/api/approvals/{id}/approve` 与 `/api/approvals/{id}/reject`）及全流程事件审计；
  - 审批通过后无缝恢复后续 DAG 拓扑节点执行，审批驳回时优雅将 Step 标记为 `FAILED` 并级联熔断中止整个 Run，全程 SSE 实时直推；
- **Flyway 数据库演化 (`V5__phase4_workflow_dag_and_orchestration.sql`)**：
  - 新增与丰富 `workflow_definitions` (name, description, updated_at), `workflow_runs` (context_data_json), `step_runs` (inputs_json, outputs_json, requires_approval) 以及 `approvals` 表索引与审计字段；
- **全绿灯测试矩阵**：
  - 新增 6 大测试套件：`WorkflowDslValidationAndCycleDetectionTest`、`SafeExpressionEvaluatorAndDataFlowTest`、`WorkflowDagSchedulingAndParallelExecutionTest`、`WorkflowBranchingAndSkipPruningTest`、`HumanInTheLoopApprovalIntegrationTest` 与 `WorkflowDefinitionAndApprovalControllerIntegrationTest`；
  - 后端 127/127 项单元测试、集成测试与架构守卫规则 100% 绿灯通过；前端 Next.js 14 生产构建 100% 成功。

---

## [v1.3.0] - 2026-10-08

### 🌟 阶段 3：执行内核、状态机与 SSE 流式事件 (Phase 3 Deliverables)
- **严格 Run/Step 状态机 (`ExecutionStateMachine`)**：
  - 落地 `WorkflowRunStatus` 与 `StepRunStatus` 8 大核心生命周期状态（`PENDING`, `RUNNING`, `PAUSED`, `WAITING_APPROVAL`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`）；
  - 严格定义并拦截非法状态跃迁（终态不可逆，非法跳转抛出专用错误码 `6007 INVALID_STATE_TRANSITION`）；
  - 完善步骤与工作流重试机制：支持 `FAILED` 与 `TIMED_OUT` 单步通过 `retryStep` 重置为 `PENDING` 并递增 `attempt` 次数，工作流重启为 `RUNNING` 时自动清空旧的终止态与超时时间戳；
  - 状态机流转事件全量委托 `RunEventBroadcaster` 统一发布，保障 `RUN_STATE_CHANGED` / `STEP_STATE_CHANGED` 实时直推 SSE，彻底消除了直接绕过广播器导致的序号失序与碰撞问题；
  - 基于 `runId` 的细粒度重入锁确保高并发竞态下的转移互斥，并在完成执行后提供安全的内存状态回收方法；
- **工作流取消与中断协作机制 (`CancelToken` & `CancelTokenRegistry`)**：
  - 实现非阻塞 `CancelToken` 信号机制，支持优雅终止回调注册；
  - 跨平台进程树递归强杀：结合 `ProcessHandle.descendants()` 递归回收与 Windows `taskkill /PID <pid> /T /F` 双层防御，彻底杜绝包装脚本下的孤儿进程残留；
  - 提供看门狗超时监控调度器，执行超时时自动触发强平看门狗并将 Run 收敛为 `TIMED_OUT`；
  - 级联取消所有待执行与活跃 Step，并持久化 `RUN_CANCELLED` / `RUN_TIMED_OUT` 事件；
- **真实执行解耦与 Runtime SPI (`AgentRuntime` & `ExecutionScheduler`)**：
  - 调度器 `ExecutionScheduler` 专注于节点拓扑编排、状态流转、重试策略、超时监控与事件沉淀，杜绝无界线程池 OOM 隐患，采用具名有界线程池及 `@PreDestroy` 优雅停机回收；
  - 抽象并落地 `AgentRuntime`、`ExecutionHandle`、`RuntimeDescriptor`、`RuntimeHealth` 与 `RuntimeEventSink` 标准接口；
  - 落地 `MockAgentRuntime`（测试与确定性离线模拟，明确标记 `simulated = true`）、`OpenAiCompatibleRuntime`（标准 HTTP 流式 API、推理内容容错与密钥脱敏）、`CliAgentRuntime`（本地子进程树与超时回收）；
  - 提供 `AgentRuntimeRegistry` 动态路由器，支持健康检查与降级处理；
- **实时 SSE 流式输出与 Last-Event-ID 断点重连 (`RunEventBroadcaster`)**：
  - 提供真实 SSE 流式输出端点 `GET /api/executions/runs/{runId}/stream` 与 `GET /api/runs/{runId}/stream`，彻底淘汰无状态长轮询假实时；
  - 每个事件具备单调递增 `sequenceId` 并持久化落库；
  - 完整实现 `Last-Event-ID` 请求头与 `lastEventId` 查询参数的断点续传重放，并增加 `PageRequest` 2000 条上限安全保护，防范海量事件回放 OOM；
  - `AuthFilter` 增强支持标准浏览器 `EventSource` 的 URL Token 查询参数认证；
  - 提供自动化 20 秒 `:heartbeat` 心跳保活机制，防止网络代理断开；
- **Flyway 数据库演进**：
  - 落地 `V4__phase3_execution_kernel_and_state_machine.sql`，为 `workflow_runs` 添加 `cancel_reason`, `cancelled_at`, `correlation_id`，为 `step_runs` 添加 `started_at`, `finished_at`, `duration_ms`, `correlation_id`，并建立状态查询高效索引；
- **全绿灯测试矩阵**：
  - 新增 `ExecutionStateMachineAndConcurrencyTest`、`WorkflowCancellationAndTimeoutTest`、`ExecutionSseStreamAndReconnectionTest` 与 `ExecutionSchedulerAndRuntimeIntegrationTest` 20 项集成测试用例；
  - 后端 99/99 项单元、架构守卫与集成测试 100% 绿灯通过；前端 Next.js 14 生产构建 100% 绿灯。

---

## [v1.2.1] - 2026-10-08

### 🐛 缺陷修复：跨平台 Linux/Windows 路径安全归一化与 CI 全绿修复
- **Linux CI 跨平台路径解析逃逸修复**：
  - 定位并修复 Ubuntu/Linux 下因反斜杠 `\` 不作为路径分隔符导致的目录遍历逃逸与 `.git` 拦截绕过问题；
  - 在 `DefaultWorkspaceResolver` 入口处强制收敛并归一化客户端路径（将 `\` 统一为 `/`），补全早期字符串切片 `.git` 与 NTFS 短名双层防护；
  - 增强跨平台 Windows 绝对驱动器盘符（如 `C:/...`）在 Linux 运行环境下的识别与拦截，确保统一抛出 `WORKSPACE_TRAVERSAL_DENIED (3004)`；
  - 修复 `WorkspaceLockManager.normalizeKey` 在 Linux 下因反斜杠未归一化导致的锁键不一致问题；
  - 远程 GitHub Actions CI 两个 Job（`Backend Tests & Build` 与 `Frontend Build & Typecheck`）全部成功通过（2/2 绿灯）。

---

## [v1.2.0] - 2026-10-08

### 🌟 阶段 2：安全工作区与 JGit 审计升级 (Phase 2 Deliverables)
- **深化受控工作区管理与多维沙箱防御**：
  - `WorkspaceResolver` 升级为基于 `workspaceId` + `relativePath` 受控解析，自动绑定数据库工作区或受控根目录；
  - 完善 `Path.normalize()`、`toRealPath()`、符号链接越权检测与根目录 Containment 校验；
  - 严格拦截设备文件（包含 Windows 保留字 `CON`, `PRN`, `AUX`, `NUL`, `CONIN$`, `CONOUT$`, `COM0-9`, `LPT0-9` 及其变种拓展名，专用错误码 3009）；
  - 严格拦截 `.git` 内部篡改、大小写变种（`.GIT`, `.Git`）、尾随点（`.git.`）及 Windows NTFS 8.3 短名称（`git~1` / `GIT~1`）；
  - `WorkspaceApplicationService` 严格接入跨租户归属权校验，全面防御 IDOR 水平越权；
- **统一受控工作区文件操作 API 与下载归档导出**：
  - 暴露标准 RESTful 控制器 `WorkspaceController`（`/api/workspaces/{workspaceId}/tree`, `/file`, `/file/rename`, `/file`, `/diff`, `/download`, `/archive`, `/lock/*`）；
  - 建立单文件 10MB 写入上限防御与 2MB 预览上限限制；
  - 增加二进制文件检测引擎（扩展名 + 首 4KB 空字节探测），禁止直接返回乱码，提供安全截断与结构化视图；
  - 提供单文件安全二进制流下载（`GET /{workspaceId}/download`）与整工作区排除 `.git` 的 ZIP 归档打包导出（`GET /{workspaceId}/archive`）；
- **JGit 审计与快照治理**：
  - 每次 Workflow Run 启动时自动建立 JGit 基线 commit（`Baseline commit for Run <runId>`）并打上基线 Tag；
  - 每个重要 Step 完成时自动创建快照 commit（`[StepSnapshot]`）与 Tag，精确统计变更文件清单并计算 SHA-256 校验和落库；
  - 修复 JGit Repository 在 try-with-resources 下 close() 双重调用的底层警告；
- **结构化 Diff 输出引擎**：
  - JGit 模块输出结构化 Diff 对象（`StructuredDiff` 与升级版 `FileDiffEntry`），涵盖文件变更类型（ADD/MODIFY/DELETE/RENAME/COPY）、增减行数、标准 Unified Diff patch、是否二进制、是否重命名、冲突标记（`hasConflict`）及审查状态；
- **工作区并发与租约治理 (Keyed Lock & DB Lease Sync)**：
  - 升级 `WorkspaceLockManager` 支持多 Owner 身份识别与租约超时（Lease TTL）模型；
  - 实现基于 `workspaceResolver` 的 Key 归一化，彻底消除 `workspaceId` 与物理文件路径之间的锁冲突不一致漏洞；
  - 落地与数据库 `workspace_locks` 租约表的持久化双写与跨实例协同，支持租约续期与超时防死锁自动回收（Deadlock Auto-Recovery）；
  - 引入锁释放时的无等待线程安全淘汰机制，彻底根除长期运行下的 `ConcurrentHashMap` 内存泄漏风险；
- **产物审查与安全非破坏性回滚**：
  - 实现 `ArtifactApplication` 与 `ArtifactController`，支持对 Step 产物执行 `accept`、`reject`、`revert`；
  - 回滚必须严格限定在受控 snapshot 基线内，禁止硬重置工作区（杜绝 `git reset --hard` 清空用户未提交工作的风险），仅将目标 Step 影响的文件还原至基线并产生审计 Revert Commit；
  - 支持快照元数据缺失时自动通过 Git Commit Diff 反向推导变更文件清单，并增加回滚路径越权与无效基线提交检查；
- **Flyway 数据库演进**：
  - 落地 `V3__phase2_workspace_audit_artifacts.sql`，为 `artifacts` 表补充 `review_status`, `reviewed_by`, `reviewed_at`, `review_comment` 审查字段与分布式锁租约表 `workspace_locks`；
- **全绿灯测试矩阵**：
  - 新增 `WorkspaceSecurityAndJGitAuditIntegrationTest` 19 项边界防御与深层功能测试用例（覆盖设备名拦截、NTFS 短名保护、下载与 ZIP 归档、数据库租约表协同、ID 与路径锁归一化、跨租户越权拦截、快照自动推导与安全回滚）；
  - 后端 79/79 项单元、架构守卫与集成测试 100% 绿灯通过；前端 Next.js 14 生产构建 100% 绿灯。

---

## [v1.1.0] - 2026-10-08

### 🌟 阶段 1：领域重构与持久化地基 (Phase 1 Deliverables)
- **九大核心业务领域划分与分层重构**：
  - 建立 `identity`, `project`, `agent`, `conversation`, `orchestration`, `execution`, `runtime`, `audit`, `sandbox`, `shared` 模块包结构；
  - 彻底解耦 Controller 与持久化 Repository，建立 Controller 仅依赖 `*Application` 契约体系；
  - 建立 Command / Query / View DTO 传输层规范，消除 JPA Entity 直接对外暴露；
- **Flyway 数据库版本演进与 18 张核心业务表迁移**：
  - 落地 `V1__init_schema.sql`，建立 `users`, `projects`, `workspaces`, `agent_definitions`, `agent_instances`, `providers`, `teams`, `team_members`, `conversations`, `conversation_participants`, `messages`, `workflow_definitions`, `workflow_runs`, `step_runs`, `run_events`, `artifacts`, `approvals`, `deployments` 共 18 张核心业务表结构；
  - 优化核心联合索引（`idx_messages_conv_seq`, `idx_run_events_run_seq`, `idx_wf_runs_idemp`），兼顾 H2 (MySQL Mode) 与 MySQL 8.0 双重适配；
  - 落地 `V2__seed_system_baseline.sql` 初始系统基线数据；
  - 会话参会人淘汰逗号拼接，重构为 `conversation_participants` 关系表；消息流增加单调递增 `sequence_num` 与 `schema_version = "v1"` 结构定义；
- **认证鉴权、水平越权防御与链路治理**：
  - 实现基于 Token 认证体系（包含 HMAC-SHA256 签名、随机 Nonce 防碰撞与登出黑名单机制）及 BCrypt 强哈希密码校验；
  - 提供完整认证生命周期接口：`register`, `login`, `refresh`, `logout`, `me`；
  - 彻底剔除未认证请求隐式降级至 `user-1` 的安全旁路漏洞，严格要求所有受控接口携带有效身份令牌；
  - 新增 `ResourceAccessGuard`，实施跨用户/跨租户资源归属强制鉴权（403 `FORBIDDEN`）；
  - 全链路集成 `RequestCorrelationFilter`，自动透传 `X-Request-ID` 与 `X-Correlation-ID`，并在统一响应体 `Result<T>` 中返回 `requestId`；
  - 全局配置 `spring.jpa.open-in-view: false`，规范事务边界；
- **执行引擎 RESTful 控制器与事件回放**：
  - 新增 `ExecutionController`，对外暴露运行启动（幂等防重）、状态查询、步骤获取与事件断点续传（`afterSeq` 游标）；
  - 执行引擎全链路校验目标项目的归属权，杜绝跨租户执行注入；
- **自动化架构守卫与 60 项防御性测试矩阵**：
  - 引入 ArchUnit 架构自动化守护 (`ArchUnitArchitectureTest`)，强制约束 Controller 与 Repository 的调用隔离、分层归属以及严禁直接泄露 JPA 实体对象；
  - 修复 `GlobalExceptionHandler` 对领域业务异常 `BusinessException` 的捕获穿透缺陷，统一错误码字典；
  - 新增 `RestApiSecurityAndMultiTenancyIntegrationTest`，对注册/登录/刷新/注销、无凭证访问拒绝、跨用户 Project/Workspace/IM 会话越权拦截进行真实 MockMvc HTTP 端到端检验；
  - 后端 60/60 项单元、集成与防御性测试矩阵 100% 绿灯通过；前端 Next.js 14 生产构建通过。

---

## [v1.0.0] - 2026-10-06

### 🌟 核心特性
- **企业级 DDD 四层架构体系**：完成适配层、应用层、领域层与基础设施层的四层解耦，搭建高内聚低耦合的 Spring Boot 3.3.4 后端工程基石；
- **统一智能体 SPI 插件引擎 (UnifiedAgentAdapter)**：抽象多运行时统一接入契约，打通 Claude Code、Codex CLI、OpenClaw 与 DeepSeek V3 API 的双向流式通信；
- **飞书级群聊 IM 协作总线**：支持单聊、多会话并发管理与 `@Mention` 智能指令路由；集成 Orchestrator 任务拓扑拆解引擎与富文本交互卡片（Interactive Cards）；
- **原生 JGit 工作区版本审计**：基于 Eclipse JGit 内核自研行级增删 Unified Diff 计算引擎，精准捕获多 Agent 编写代码的每一处变动；
- **现代化 Bento Grid 协作控制台**：基于 Next.js 14 + Tailwind CSS 实现现代化暗黑美学前端大厅，内置可视化 DAG 工作流拖拽画布、物理工作区文件树与代码编辑器、实时 Web 预览沙箱与 Docker 一键容器化部署。

### 🛡️ 安全与工程规范
- **全方位凭证脱敏与分层隔离**：全面移除代码库内任何硬编码密钥，改用环境变量与本地安全文件隔离方案；
- **全量单元与边界防御测试**：13/13 项单元测试与集成测试矩阵全部 100% 绿灯通过；
- **AOCI (AI-Oriented Code Indexing) 架构代码地图**：在根目录建立符号-语义高密度索引标准，杜绝长程研发中的架构遗忘。
