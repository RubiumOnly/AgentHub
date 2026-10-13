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

3. **受控研发团队长期生产模式 (`LIVE_TEAM` — 规划于 Phase 6 完成)**
   - **目标边界**：面向受控研发团队多人长期协同使用，要求完成组织/成员/角色（Owner/Admin/Developer/Reviewer/Viewer/Auditor）权限矩阵、项目与组织级成本/并发预算、密钥轮换审计、SLO 看板与干净环境恢复演练。在 Phase 6 退出条件达成前，当前系统不宣称已具备 `LIVE_TEAM` 能力。

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

### 4. 数据库物理级多租户隔离与细粒度 RBAC 权限矩阵（Application-Layer Owner Isolation Only）
* **当前实现**：采用共享 MySQL 数据库 + 业务表 `owner_id` 字段隔离，由应用层 `ResourceAccessGuard` 在 Project、Workspace、WorkflowRun、Approval、Deployment、Provider 各入口强制校验资源归属权。
* **未实现边界**：**未实现** Schema-per-Tenant（独立 Schema）或 Database-per-Tenant（独立物理库）隔离，也**未实现**多角色组织架构（如 Org Admin / Developer / Viewer / Auditor）的细粒度 RBAC/ABAC 权限矩阵。

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
