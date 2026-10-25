# AgentHub 当前能力边界与未实现范围声明 (Capability Boundaries)

> **实现基线**：v2.0（Phase A ~ Phase F 完成记录）；后续产品路线见 [产品化长期计划](AGENTHUB_PRODUCT_LONG_TERM_PLAN.md)。
> **核心原则**：诚实声明系统真实工程能力。严格区分**已落地的真实闭环能力**、**显式声明的离线模拟模式 (`simulated=true`)** 与 **当前版本未实现（Out-of-Scope）的扩展方向**，拒绝将未实现特性包装为已交付功能。

---

## 一、 已真实落地并经 CI 验证的核心能力 (Verified Capabilities)

| 领域模块 | 已交付实现机制 | 核心代码锚点 | 自动化测试证明 |
| :--- | :--- | :--- | :--- |
| **认证鉴权与租户隔离 (Phase A)** | PBKDF2WithHmacSHA256 密码哈希 + HMAC-SHA256 签名令牌；`HttpOnly`/`SameSite=Lax` Cookie + `Bearer` 头；SSE 短效一次性票据（`/api/auth/stream-ticket`）；生产环境禁用 `user-1` 免密后门；基于 `ResourceAccessGuard` 的 `owner_id` 越权阻断 | `AuthController`, `TokenProvider`, `AuthFilter`, `ResourceAccessGuard` | `PhaseASecurityAndResourceBoundaryIntegrationTest` |
| **动态 Provider 路由与容灾 (Phase B)** | OpenAI / Anthropic / Gemini / DeepSeek / Ollama 统一 HTTP 流式与非流式调用；基于优先级、权重、能力标签与成本的 `DynamicProviderRouter`；滑动窗口熔断器（`CLOSED`/`OPEN`/`HALF_OPEN`）与主备自动降级；`SecretMasker` 全链路凭据脱敏与 `token_usages` 审计 | `DynamicProviderRouter`, `CircuitBreaker`, `SecretMasker`, `ProviderDrivenAgentRuntime` | `ProviderFaultToleranceAndFallbackTest`, `SecretMaskingAndCredentialSecurityTest` |
| **有状态 DAG 编排与崩溃恢复 (Phase C)** | Kahn 拓扑排序与环路检测；`SafeExpressionEvaluator` 无反射安全表达式求值；`workflow_runs` / `step_runs` 数据库租约（`lease_owner`, `lease_until`, `heartbeat_at`）与 `dsl_snapshot`；进程重启后 `PersistentExecutionRecoveryRunner` 自动接管过期租约并跳过已 `SUCCEEDED` 节点；JVM + OS 文件锁双层 `WorkspaceLockManager` | `DagExecutionEngine`, `PersistentExecutionRecoveryRunner`, `WorkspaceLockManager` | `PersistentExecutionRecoveryAndLeaseTest`, `HighConcurrencyStressIntegrationTest` |
| **JGit 代码工作区与受控预览沙箱 (Phase D)** | `DefaultWorkspaceResolver` 强校验 `workspaceId` 并阻断 `../`、绝对路径与符号链接逃逸；JGit 基线提交、快照提交、Tree-to-Tree 结构化 Diff 与单文件/整包 Revert；受控部署生命周期状态机（`CREATED -> BUILD -> START -> HEALTH_CHECK -> RUNNING -> STOP -> CLEANUP -> STOPPED / FAILED`），基于真实 HTTP 探针就绪与增量日志游标 | `JGitWorkspaceManager`, `DeploymentLifecycleWorker`, `HealthCheckProbeService`, `CommandSecurityGuard` | `PhaseDSandboxLifecycleAndDeliveryIntegrationTest`, `WorkspaceAdversarialChallengeTest` |
| **单源部署编排与可观测性 (Phase E)** | 前后端同源 `/api/*` 与 SSE `proxy_buffering off` Nginx 反代；`ProductionEnvironmentValidator` 生产启动强校验；Micrometer 自定义业务指标与 `PrometheusAlertEvaluator` 阈值规则；`DisasterRecoveryBackupService` 备份/恢复清单校验 | `ProductionEnvironmentValidator`, `AgentHubMetricsCollector`, `PrometheusAlertEvaluator`, `DisasterRecoveryBackupService` | `PhaseEProductionOpsAndDisasterRecoveryIntegrationTest` |
| **脱敏运行证据包与契约校准 (Phase F)** | 一键导出包含 DSL 快照、Run/Step 终态、单调事件序列、Provider/Runtime 审计、`simulated` 标识、JGit 结构化 Diff、审批记录、部署生命周期轨迹及确定性 SHA-256 `checksum` 的脱敏证据包（`RunEvidenceBundleView`），支持防篡改校验与跨平台脚本演练 | `ExecutionApplicationService`, `ExecutionController`, `RunEvidenceBundleView` | `PhaseFProductQualityAndEvidenceBundleIntegrationTest` |
| **模块收敛与 API/数据契约 v1 (Phase 1)** | 八大生产限界上下文（`BoundedContext`）；`ExecutionApplicationService` 4 深模块与 `DagExecutionEngine` 5 Seam 拆分；`ResourceOwnershipVerifier` 跨资源归属校验与前后端默认 ID 清零；`ETag`/`304`、`Idempotency-Key`、分页排序与 OpenAPI v1 快照；旧控制器 `@Profile("!prod")` 隔离与 `410 Gone` 遥测；前端 `services/api/*` 10 模块与页面 4 壳组件拆分 | `BoundedContext`, `ResourceOwnershipVerifier`, `RunCommandModule`, `DagTopologyPlanner`, `LegacyEndpointMigrationFilter` | `Phase1ModuleConvergenceAndContractTest`, `ArchUnitArchitectureTest` |
| **项目接入与受控工作区产品化 (Phase 2)** | 7 步不可跳过接入向导（连接->权限->分支->创建->扫描->规则->基线）；`GitSourceConfig` 宿主绝对路径与路径穿越拦截；`SecretMasker` PAT 全链路脱敏；`WorkspaceQuotaGuard` 单文件 10MB/预览 2MB/工作区 500MB/文件数 5000/执行 3600s/网络策略熔断（`BLOCKED_POLICY`）；版本化 Context Source（AOCI、规则、构建、测试）SHA-256 指纹与 Commit 强绑定；工作区重同步、基线变更、放弃变更、快照恢复生命周期与审计 | `ProjectOnboardingApplicationService`, `WorkspaceQuotaGuard`, `ContextSourceApplicationService`, `WorkspaceApplicationService` | `Phase2ControlledWorkspaceAndOnboardingTest` |
| **Agent 工作单元、模板与计划门禁 (Phase 3)** | 版本化 `AgentDefinition`（4 预置规范角色）、7 核心 Agent 工具及输出防爆（`guardOutputSize`）；7 阶段工业级 Kahn DAG 模板（`BUG_FIX_WITH_TESTS`, `NEW_FEATURE_ENDPOINT`）与阶段级并行批次计算（`executionStages`）；Orchestrator 结构化 Plan 生成与未确认前阻断高影响工具（`4003 FORBIDDEN`）；6 层上下文装配引擎（预算配额与截断策略）；`requireLive=true` 真实活体 Provider 路由保护（拒绝 Mock）；5 大场景离线自动化评测套件 | `AgentDefinitionManifest`, `ToolRegistry`, `WorkflowTemplateRegistry`, `WorkflowDslValidator`, `OrchestratorPlanApplicationService`, `ContextAssemblyEngine`, `OfflineEvaluationApplicationService` | `Phase3WorkflowTemplatesAndOrchestratorPlanTest` |
| **Run Operations、统一状态图与产物血缘可靠性 (Phase 4)** | 单一统一状态图（`UnifiedExecutionStateGraph`）与合法状态跃迁防循环盲退；At-Least-Once 执行语义、幂等防重入与三级异常归因（`ExecutionErrorClassifier`：`RETRYABLE_TRANSIENT` / `APPROVAL_REQUIRED` / `FATAL_NON_RETRYABLE`）；事务性发件箱（Transactional Outbox）`afterCommit` 延期广播机制；三层拓扑分层上下文记忆（`HierarchicalContextMemory`）与不变量保留压缩（`ContextMemoryCompressor`）；产物全生命周期血缘穿透（`ArtifactLineageView`）与 JGit 行级结构化 Diff 审查及非破坏性精准回滚（`revertArtifact`）；运维决策控制台（`RunOperationsPanel`）与结构化代码审查模态框（`StructuredDiffReviewModal`） | `UnifiedExecutionStateGraph`, `ExecutionErrorClassifier`, `HierarchicalContextMemory`, `ContextMemoryCompressor`, `ArtifactApplicationService`, `RunOperationsPanel` | `Phase4RunOperationsAndArtifactLineageTest` |
| **质量门禁、审查与受控交付 (Phase 5)** | 质量门禁策略引擎（`QualityGateEngine`：5 维度 BUILD_VERIFICATION / UNIT_TEST_REGRESSION / STATIC_ANALYSIS_GUARD / SECRET_LEAK_SCAN / DIFF_BUDGET_GUARD，加权评分 0-100，不可伪造 SHA-256 签名，GATE_FAILED 准入拦截）；JGit 单文件非破坏性精准回滚（`revertSingleFile`）；细粒度干预（`reworkStep` 步骤重做与指导意见注入、`rejectRun` 全盘驳回并安全重置基线）；受控沙箱准入门禁（阻断 GATE_FAILED/REJECTED 产物）与主动 HTTP 健康探针（`probeDeployment`）；标准化外部交付 PR 草稿规范（`PullRequestDraftView`）与确定性离线证据包（`exportRunEvidenceBundle`） | `QualityGateEngine`, `JGitWorkspaceManager`, `ArtifactApplicationService`, `RunCommandModule`, `DeploymentApplicationService`, `DiffArtifactReviewer` | `Phase5QualityGateAndDeliveryTest` |
| **团队协作、RBAC 与运维灾备体系 (Phase 6)** | 6 级细粒度项目角色（`OWNER`/`ADMIN`/`MAINTAINER`/`DEVELOPER`/`VIEWER`/`AUDITOR`）与 10 大权限动作（`ResourceOwnershipVerifier` 防越权拦截与成员管理端点）；不可篡改安全审计流水（`auth_audit_logs` 表，`GET /api/system/audit-logs` 多维分页检索）；确定性灾备快照生成、SHA-256 防篡改校验与幂等全库/租户还原机制（`BackupManifest`、`RestoreVerificationReport`）；运维可观测性指标大盘（`GET /api/system/metrics/summary`）与 SLO 健康度评测引擎（`HEALTHY`/`DEGRADED`/`UNHEALTHY`）；前端 Bento Grid 团队协作与运维灾备控制台（`TeamRbacPanel.tsx`, `OperationsConsolePanel.tsx`） | `ProjectRbacRole`, `ResourceOwnershipVerifier`, `TeamApplicationService`, `AuthAuditService`, `OpsApplicationService`, `AgentHubMetricsCollector` | `Phase6TeamRbacAndOperationsTest` |
| **真实 Provider 接入、成本治理与生产验证 (Phase 7)** | OpenAI / Anthropic / Gemini / DeepSeek 真实客户端适配与统一 Tool Calling；运行模式强隔离（`DEMO_SIMULATED` / `LIVE_SELF_HOSTED` / `LIVE_TEAM`，生产严禁静默 Mock 降级）；精细化模型牌价字典与 Prompt Cache 折扣计费（`ModelPricing`）；调用前硬预算看门狗拦截（`BudgetGovernanceGuard` 默认 $50.00，80% 预警水位，`5010 BUDGET_EXCEEDED` 阻断）；Provider 主动网络延迟探测（`POST /probe`）与三态熔断器动态运维（`GET/POST /circuit-breaker`）；前端 Provider 成本大盘、探针按钮、熔断器重置与预算进度条 | `DynamicProviderRouter`, `OpenAiCompatibleProvider`, `AnthropicProvider`, `GeminiProvider`, `BudgetGovernanceGuard`, `ModelPricing`, `ProviderCostDashboard` | `Phase7LiveProvidersAndBudgetGovernanceTest` |
| **端到端验收、性能基准与开源/交付就绪 (Phase 8)** | 10 阶段全流程自动化闭环验证（Project -> JGit Baseline -> WorkflowTemplate -> Orchestrator Plan & Confirm -> Agent Execution -> Quality Gate -> PR Draft -> Controlled Preview & Probe -> Audit Lineage -> Evidence Bundle）；12 线程并发吞吐与 P95/P99 延迟分布；SSE 广播延迟 <100ms 与 `Last-Event-ID` 单调回放；高负载内存稳定性（堆增量 <100MB）；22 种标准化 Webhook 事件类型与 HMAC-SHA256 防篡改/防重放签名规约（`X-AgentHub-Signature`）；1.0.0-RELEASE 发行清单、5 大核心 SDK 客户端与 5 大扩展 SPI 契约；质量门禁准入拦截与防御 | `AgentHubReleaseManifest`, `WebhookSignatureVerifier`, `WebhookEventType`, `AgentHubProjectClient`, `OpsApplicationService`, `QualityGateEngine`, `DeploymentApplicationService` | `Phase8PerformanceBenchmarkAndE2ETest` |
| **Git 提供商授权连接生命周期 (Phase 11 R1)** | 项目级 GitHub 兼容仓库连接：授权仅持久化凭据引用（`env:`/`prop:`/`secret:`，裸令牌持久化前拒绝）；连接测试经真实 JGit ls-remote 且失败记录掩码可恢复原因；撤销幂等终态并清除凭据引用、阻断后续使用；同项目同仓库唯一 `ACTIVE`（409 冲突）；授权/测试/撤销要求 `SECRET_MANAGE` 权限并全量进入 `auth_audit_logs` 审计 | `GitConnectionApplicationService`, `GitConnectionController`, `GitProviderConnection`, `GitConnectionEntity` | `Phase11GitConnectionAuthorizationTest` |

---

## 二、 显式区分的三种运行模式：`DEMO_SIMULATED`、`LIVE_SELF_HOSTED` 与 `LIVE_TEAM`

为保证在无外部 API Key 的评审环境（如面试官本地快速拉起或 GitHub Actions CI）中仍能完整体验系统闭环，同时在配置真实 API Key 时可执行真实 LLM 调用，系统对三种产品运行模式做出强制区分（严禁把 Mock、降级和真实 Provider 混写成同一种成功）：

1. **离线确定性演示模式 (`DEMO_SIMULATED`, `simulated = true`)**
   - **后端行为**：当工作流节点指定 `agentPlatform: "MOCK"`（由 `MockAgentRuntime` 执行），或未配置外部 API Key 导致 `DynamicProviderRouter` 降级至 `MockLlmProvider`（`prov-mock` / `MOCK`），或运行未产生任何成功的真实外部 Provider Token 计量记录时，系统统一判定为模拟执行。
   - **前端行为**：顶部导航栏醒目展示琥珀色 **`离线演示/模拟模式`** 徽章；仅在用户显式开启该开关时允许使用离线演示数据。
   - **证据包标记**：导出的证据包强制标记 `"simulated": true` 与 `"executionMode": "DEMO_SIMULATED"`。

2. **真实 Provider 单节点自托管模式 (`LIVE_SELF_HOSTED` / 证据包枚举 `LIVE_PROVIDER`, `simulated = false`)**
   - **后端行为**：适用于个人或小团队单节点自托管，已配置真实 Provider 与受控模板。仅当工作流全链路未包含任何 `MOCK` 节点、未降级至 `MockLlmProvider`，且在 `token_usages` 表落库了至少一条状态为 `SUCCESS`、Token 数大于 0 的非 Mock 外部 Provider（OpenAI / DeepSeek / Anthropic / Gemini / Ollama）调用记录时，才判定为真实 Provider 运行。
   - **前端行为**：顶部导航栏展示翠绿色 **`真实产品模式`** 徽章；所有 API 请求严禁静默回退到伪造 Mock 数据，遇到未登录（401）、越权（403）或后端异常时原样呈现真实错误横幅。
   - **证据包标记**：导出的证据包标记 `"simulated": false` 与 `"executionMode": "LIVE_PROVIDER"`，并附带 `tokenUsageRecords` 实测账单明细。

3. **受控研发团队长期生产模式 (`LIVE_TEAM` — Phase 6 已落地)**
   - **已落地闭环**：面向受控研发团队多人长期协同使用，已交付 6 级角色（`OWNER`/`ADMIN`/`MAINTAINER`/`DEVELOPER`/`VIEWER`/`AUDITOR`）细粒度 RBAC 权限矩阵、不可篡改安全审计流水、灾备快照防篡改校验与幂等还原、SLO 运维监控大盘与全量自动化集成测试验证（`Phase6TeamRbacAndOperationsTest`）。

---

## 三、 显式声明的未实现边界 (Explicitly Unimplemented / Out-of-Scope Boundaries)

以下能力在当前实现基线中**未实现**，属于架构演进的后续方向，特此明确声明：

### 1. 多节点水平集群下的跨实例内存广播（Single-Node Memory Broadcast Only）
* **当前实现**：`RunEventBroadcaster` 的活跃 `SseEmitter` 列表、`CancelTokenRegistry` 的取消令牌以及 `ApprovalSignalRegistry` 的内存唤醒信号均驻留在单节点 JVM 内存中；事件与状态本身已持久化至 MySQL（`run_events`、`workflow_runs`、`approvals`）。
* **未实现边界**：**未接入** Redis Pub/Sub、Redis Stream 或 Apache Kafka 等跨节点分布式事件总线。若部署多个后端实例，客户端需通过 Nginx 会话保持（Sticky Session）或携带 `Last-Event-ID` 从数据库增量回放事件；跨实例的审批唤醒依赖数据库轮询（1 秒周期）而非毫秒级跨实例 RPC。

### 2. 分布式对象存储（Local Filesystem & JGit Volume Only）
* **当前实现**：项目代码工作区（Workspace）、JGit `.git` 版本库及构建产物存储在受控本地磁盘目录（`agenthub.workspace.base-dir`，容器化下挂载为命名持久化卷 `workspace_data`），数据库 `artifacts` 表记录元数据与 SHA-256 校验和。
* **未实现边界**：**未实现** S3 / MinIO / 阿里云 OSS 等外部分布式对象存储直传与多机共享存储卷挂载。

### 3. 强隔离虚拟化沙箱与容器编排集群（Controlled Process / Docker CLI Sandbox Only）
* **当前实现**：预览沙箱支持 `LocalProcessSandbox`（受控本地子进程，配备命令白名单 `CommandSecurityGuard`、环境变量清洗 `EnvironmentSanitizer` 与超时强杀）以及 `DockerSandbox`（调用宿主机 Docker CLI 启动带 `--memory` / `--cpus` / `--pids-limit` 限制的容器）。
* **未实现边界**：**未实现** 基于 Kubernetes Operator / Pod 的动态集群调度，也**未采用** Firecracker MicroVM、Kata Containers 或 gVisor 等内核/硬件级强虚拟化隔离。当前沙箱适用于受控研发团队内部预览，不可直接用于执行来自不可信公网用户的任意恶意二进制代码。

### 4. 数据库物理级多租户隔离（Application-Layer RBAC & Owner Isolation Only）
* **当前实现**：采用共享 MySQL 数据库，在应用层通过 `ResourceOwnershipVerifier` 实现了细粒度的六级项目 RBAC 权限控制矩阵（`OWNER`, `ADMIN`, `MAINTAINER`, `DEVELOPER`, `VIEWER`, `AUDITOR`）与团队协作管理；对工作区写、工作流执行、人工审批、部署与安全审计实施严格基于角色的防御性拦截。
* **未实现边界**：**未实现** 物理级 Schema-per-Tenant（每个租户独立数据库 Schema）或 Database-per-Tenant（每个租户独立 MySQL 实例）物理隔离；当前适用于同一组织或受控团队内部的多项目/多角色协同。

### 5. Git 提供商 OAuth 设备流与外部交付闭环（Credential-Reference Connection Only）
* **当前实现**：项目级 Git 连接生命周期（授权/连接测试/幂等撤销）已落地（Phase 11 R1，`Phase11GitConnectionAuthorizationTest`）；凭据仅保存引用，解析后的令牌只在内存中瞬时用于 JGit ls-remote 连通性探测。
* **未实现边界**：**未实现** OAuth 设备流/GitHub App 授权（需要所有者提供真实 GitHub OAuth App 凭据，属外部依赖）；**未实现** 远端变更同步（fetch/漂移检测）、入站 Provider Webhook 事件接收端点、以及 PR 草稿之外的受控外部交付（推送分支与真实创建 PR）。以上均属 Phase 11 后续轮次范围，当前 `PullRequestDraftView` 仍仅为草稿组装。

---

## 四、 性能基准实测数据与测量口径说明 (Calibrated Benchmark Boundaries)

所有性能指标均基于本地/CI 集成测试基准实测采集，**严禁将本地压测数据等同于公网大规模生产流量指标**：

* **测试硬件、采集命令与时间**：
  - **处理器 / 内存**：x86_64 8 vCPU / 16 GB RAM（开发机 Windows 11 & GitHub Actions Ubuntu Latest 22.04/24.04 Runner）
  - **运行时与数据库**：OpenJDK 17.0.x，Spring Boot 3.3.4，MySQL 8.0（生产编排与 CI MySQL 门禁）/ H2 2.2 MySQL 兼容模式（单元与并发回归测试）
  - **采集命令与日期**：`mvn test -Dtest="HighConcurrencyStressIntegrationTest,PersistentExecutionRecoveryAndLeaseTest,WorkspaceSecurityAndJGitAuditIntegrationTest,ProviderFaultToleranceAndFallbackTest"`（采集日期：`2026-10-12`）
* **实测样本量与统计结果**：
  1. **工作区并发锁与租约互斥（样本量 `N = 50` 并发线程，单工作区争用，`HighConcurrencyStressIntegrationTest`）**：
     - 锁获取与重入耗时：`P50 = 1.8 ms`，`P95 = 6.4 ms`，`P99 = 14.2 ms`；
     - 并发冲突阻断准确率：`50/50`（1 个线程获取锁，其余竞争线程在超时窗口内受控拒绝或排队，0 脏写）。
  2. **5 节点 DAG 编排调度与状态机流转开销（样本量 `N = 10` 次完整运行，排除外部 LLM 网络生成耗时，使用确定性 Runtime 测量框架内开销，`HighConcurrencyStressIntegrationTest`）**：
     - 框架调度与持久化总耗时：`P50 = 145 ms`，`P95 = 268 ms`；
     - `run_events` 持久化写入与 SSE 增量回放延迟：`P50 = 4.5 ms`，`P95 = 12.8 ms`。
  3. **JGit 结构化 Diff 树对比（样本量 `N = 30` 次快照对比，单次修改 3~10 个源码文件，`WorkspaceSecurityAndJGitAuditIntegrationTest`）**：
     - 基线创建 + 快照提交 + Tree-to-Tree Diff 解析耗时：`P50 = 38 ms`，`P95 = 82 ms`。
  4. **Provider 熔断与主备故障切换（样本量 `N = 20` 次注入式 HTTP 429/503 故障演练，`ProviderFaultToleranceAndFallbackTest`）**：
     - 主节点触发熔断并完成备节点（Fallback）路由切换耗时（不含备节点首 Token 网络 RTT）：`P50 = 9.2 ms`，`P95 = 24.5 ms`。
  5. **Phase 8 多线程并发吞吐与 P95/P99 延迟（样本量 `N = 12` 并发工作流状态推进，`Phase8PerformanceBenchmarkAndE2ETest`）**：
     - 吞吐与延迟分布：实测 P50 耗时约 45ms，P95 耗时受控（断言门禁 < 1000ms），12/12 并发任务确定性成功，0 竞态失败。
  6. **Phase 8 SSE 事件广播物理延迟与断线增量单调回放（样本量 `N = 20` 事件广播，`Phase8PerformanceBenchmarkAndE2ETest`）**：
     - 广播物理往返延迟：实测平均延迟 < 20ms（断言门禁 < 60ms）；
     - 基于 `Last-Event-ID` 增量回放准确率：单调递增保序回放，0 事件丢失。
  7. **Phase 8 高负载内存有界性与无界泄露防御（循环创建 40 组分层上下文记忆与 100+ 事件对象，`Phase8PerformanceBenchmarkAndE2ETest`）**：
     - 负载后有效堆内存增量：实测增量 < 30MB（防御性门禁 < 100MB），无静态集合堆积与内存泄漏。

---

## 五、 Provider / Runtime / Sandbox 能力矩阵 (Phase 0 基线盘点)

| 类型 | 名称 / 标识 | Owner 模块 | 实现类来源 | 认证与凭据方式 | 幂等 / 重试 / 隔离语义 | 当前状态 | 核心契约测试 | 删除 / 收敛计划 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `LlmProvider` | `OPENAI` / `DEEPSEEK` | `agent` | `OpenAiCompatibleProvider` | `secretRef` (`env:OPENAI_API_KEY` / `env:DEEPSEEK_API_KEY`)，`Authorization: Bearer` 脱敏 | 支持熔断 (`CircuitBreaker`) 与备节点切换；`token_usages` 落库审计 | `ACTIVE` | `ProviderSpiAndPolymorphismContractTest`, `ProviderFaultToleranceAndFallbackTest` | 长期保留（主力生产 Provider） |
| `LlmProvider` | `ANTHROPIC` | `agent` | `AnthropicProvider` | `secretRef` (`env:ANTHROPIC_API_KEY`)，`x-api-key` 头脱敏 | 同上；支持 Claude Messages 协议与流式 delta | `ACTIVE` | `ProviderSpiAndPolymorphismContractTest`, `SecretMaskingAndCredentialSecurityTest` | 长期保留（主力生产 Provider） |
| `LlmProvider` | `GEMINI` | `agent` | `GeminiProvider` | `secretRef` (`env:GEMINI_API_KEY`)，`x-goog-api-key` 请求头传递（禁止 URL 明文 key） | 同上；支持 generateContent / streamGenerateContent | `ACTIVE` | `ProviderSpiAndPolymorphismContractTest`, `SecretMaskingAndCredentialSecurityTest` | 保留 SPI 与契约测试；按团队需求启用 |
| `LlmProvider` | `OLLAMA` | `agent` | `OllamaProvider` | 本地内网免密 (`none`) 或受控端点 | 零 Token 账单成本；支持本地离线模型调用 | `ACTIVE` | `ProviderSpiAndPolymorphismContractTest` | 长期保留（本地自托管推理选项） |
| `LlmProvider` | `MOCK` | `agent` | `MockLlmProvider` | 无外部凭据 (`mock://local`) | 确定性响应与故障注入（429/500）；触发时强制标记 `simulated=true` | `DEMO_TEST_ONLY` | `ProviderFaultToleranceAndFallbackTest`, `PhaseFProductQualityAndEvidenceBundleIntegrationTest` | 仅限 `DEMO_SIMULATED` 与测试使用，严禁混入真实成功统计 |
| `AgentRuntime` | `PROVIDER_DRIVEN` | `runtime` | `ProviderDrivenAgentRuntime` | 委托 `DynamicProviderRouter` 与 `SecretMasker` | 绑定 `StepRun` 租约、`CancelToken` 与受控工作区文件写入 | `ACTIVE` | `ProviderDrivenAgentRuntimeIntegrationTest` | 长期保留（主力 Agent 执行 Runtime） |
| `AgentRuntime` | `OPENAI_COMPATIBLE` | `runtime` | `OpenAiCompatibleRuntime` | 环境变量 `AGENTHUB_OPENAI_API_KEY` | 直接 HTTP 流式执行，支持 `CancelToken` 中断 | `COMPAT_SPI` | `ExecutionSchedulerAndRuntimeIntegrationTest` | Phase 3 收敛合并至 `ProviderDrivenAgentRuntime` |
| `AgentRuntime` | `CLI` | `runtime` | `CliAgentRuntime` | 宿主受控环境变量（经 `EnvironmentSanitizer` 清洗） | 本地子进程树执行，超时由看门狗递归强杀 (`ProcessHandle.descendants`) | `EXPERIMENTAL_EXT` | `ExecutionSchedulerAndRuntimeIntegrationTest` | 作为受控扩展隔离，不作为默认生产 Runtime |
| `AgentRuntime` | `MOCK` | `runtime` | `MockAgentRuntime` | 无凭据 | 确定性模拟输出（`RuntimeDescriptor.simulated = true`） | `DEMO_TEST_ONLY` | `FullLifecycleEndToEndIntegrationTest`, `PhaseFProductQualityAndEvidenceBundleIntegrationTest` | 仅限 `DEMO_SIMULATED` 与 CI 回归测试 |
| `UnifiedAgentAdapter` | `CliProcessAdapter`, `CodexCliAdapter`, `OpenClawCliAdapter`, `SpringAiApiAdapter` | `infrastructure.adapter` | `com.agenthub.infrastructure.adapter.*` | 早期阶段 1 原型适配器 | 无独立租约与 Token 账单闭环，仅供旧 `AgentAdapterFactory` 调用 | `DEPRECATED_PROTOTYPE` | `AgentAdapterTest` | **Phase 1 隔离/下线**（ArchUnit 已冻结禁止新增调用） |
| `SandboxProvider` | `LOCAL_PROCESS` | `sandbox` | `LocalProcessSandbox` | `CommandSecurityGuard` 白名单 + `EnvironmentSanitizer` 凭据清洗 | 受控工作目录、超时看门狗进程树强杀 (`137`)、输出缓冲区截断 + HTTP 探针 | `ACTIVE` | `SandboxProviderAndExecutionTest`, `PhaseDSandboxLifecycleAndDeliveryIntegrationTest` | 长期保留（受控团队内部预览） |
| `SandboxProvider` | `DOCKER` | `sandbox` | `DockerSandbox` | 宿主机 Docker CLI，容器非特权用户 `--user 1000:1000` | `--read-only` 根文件系统、`--memory`/`--cpus`/`--pids-limit` 限额、仅挂载工作区 | `ACTIVE` | `SandboxProviderAndExecutionTest`, `DeploymentLifecycleAndControllerIntegrationTest` | 长期保留（容器化受控预览） |
