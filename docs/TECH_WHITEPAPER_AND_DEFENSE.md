# AgentHub 架构设计白皮书与技术答辩指南 (Technical Whitepaper & Defense Guide)

> **版本基线**：v2.0（已完成阶段 A ~ 阶段 F 闭环演进与证据校准）  
> **文档定位**：本白皮书面向架构评审、代码审计与技术面试答辩，所有架构陈述、性能基准与简历表述均严格对应仓库内的真实代码锚点、自动化测试与脱敏运行证据包（[`docs/evidence/sample-run-evidence-bundle.json`](evidence/sample-run-evidence-bundle.json)），绝不夸大未实现特性。

---

## 一、 核心架构定位与模式透明性（Demo Mode vs Live Provider Mode）

AgentHub 致力于解决多 Agent 辅助研发场景中的四大工程痛点：
1. **状态易失与重启丢失进度**：将纯内存 DAG 执行改造为基于 MySQL 持久化快照（`dsl_snapshot`、`inputs_json`、`outputs_json`）与数据库租约（`lease_owner`、`lease_until`）的有状态恢复引擎；
2. **多模型调用脆弱与密钥泄露风险**：构建支持动态评分选路、滑动窗口熔断降级（`CircuitBreaker`）与全链路正则脱敏（`SecretMasker`）的 `DynamicProviderRouter`；
3. **代码工作区并发踩踏与路径穿越风险**：通过 `DefaultWorkspaceResolver` 阻断路径逃逸，通过双层锁 `WorkspaceLockManager`（JVM `ReentrantLock` + OS `FileChannel` 锁）与 `JGitWorkspaceManager` 实现基线创建、结构化 Diff 与安全回滚；
4. **预览沙箱“假运行”与命令注入风险**：通过受控模板（`STATIC_WEB_TEMPLATE` 等）、`CommandSecurityGuard`、`EnvironmentSanitizer` 与真实 HTTP 探针（`HealthCheckProbeService`）驱动 8 阶段部署状态机。

### Demo Mode 与 Live Provider Mode 的严格隔离

| 维度 | 离线确定性演示模式 (`Demo Mode`) | 真实 Provider 驱动模式 (`Live Provider Mode`) |
| :--- | :--- | :--- |
| **Runtime 实现类** | `MockAgentRuntime` (`simulated = true`) | `ProviderDrivenAgentRuntime` (`simulated = false`) |
| **外部 LLM 依赖** | 零外部 API Key 依赖，用于 CI 确定性验证与快速离线体验 | 真实调用 DeepSeek / OpenAI / Anthropic / Gemini / Ollama |
| **Token 计费落库** | 不写入外部 Provider 账单 | 在 `token_usages` 表持久化真实 Prompt/Completion Tokens、实测延迟与估算成本 |
| **证据包标记** | `"simulated": true`, `"executionMode": "DEMO_SIMULATED"` | `"simulated": false`, `"executionMode": "LIVE_PROVIDER"` |

---

## 二、 核心架构图与有限状态机契约

### 2.1 DDD 模块化单体分层与限界上下文 (`ADR-001`)

系统采用模块化单体架构，按业务内聚性划分为 `identity`、`project`、`orchestration`、`execution`、`agent`、`runtime`、`sandbox`、`audit` 八个核心上下文，并由 `ArchUnitArchitectureTest` 在每次 Maven 测试时强制校验：
* 所有 `@RestController` 必须位于 `..api..` 或 `..adapter.web..` 包内；
* Controller 严禁直接注入 `..repository..` 或直接依赖/返回 `..entity..`；
* 所有以 `ApplicationService` 结尾的用例编排类必须位于 `..application..` 包内。

### 2.2 工作流与步骤 8 态状态机 (`WorkflowRunStatus` / `StepRunStatus`)

```mermaid
stateDiagram-v2
    [*] --> PENDING
    PENDING --> RUNNING: acquireLease()
    PENDING --> SKIPPED: condition == false
    PENDING --> CANCELLED: cancelRun()
    RUNNING --> WAITING_APPROVAL: requiresApproval
    WAITING_APPROVAL --> RUNNING: approve()
    WAITING_APPROVAL --> FAILED: reject()
    WAITING_APPROVAL --> CANCELLED: cancelRun()
    RUNNING --> PAUSED: pauseRun()
    PAUSED --> RUNNING: resumeRun()
    PAUSED --> CANCELLED: cancelRun()
    RUNNING --> SUCCEEDED: onStepCompleted()
    RUNNING --> FAILED: retriesExhausted
    RUNNING --> TIMED_OUT: watchdogTimeout
    RUNNING --> CANCELLED: cancelToken.isCancelled()
    FAILED --> PENDING: retryStep()
```

### 2.3 受控预览沙箱部署 8 阶段状态机 (`DeploymentStatus`)

```mermaid
stateDiagram-v2
    [*] --> PENDING: 校验 ACCEPTED Artifact & 分配动态端口
    PENDING --> BUILDING: 执行受控构建模板
    BUILDING --> STARTING: 构建退出码 == 0
    BUILDING --> FAILED: 构建失败或超时
    STARTING --> HEALTH_CHECKING: 子进程/容器存活
    STARTING --> FAILED: 进程异常退出
    HEALTH_CHECKING --> RUNNING: HealthCheckProbe 收到 HTTP 2xx/3xx
    HEALTH_CHECKING --> FAILED: 探针超时 -> 强杀进程并释放端口
    RUNNING --> STOPPING: stopDeployment() 或 TTL 到期
    RUNNING --> FAILED: 进程非预期退出
    STOPPING --> STOPPED: 销毁进程树 & 回收端口
```

---

## 三、 三个可复现的核心工程权衡 (3 Reproducible Technical Trade-offs)

### 权衡 1：模块化单体 + ArchUnit 编译期边界守护 vs 微服务拆分 (`ADR-001`)
* **为什么不拆微服务**：在单团队规模下，将认证、编排、JGit 工作区与沙箱拆分为独立微服务会引入跨服务分布式事务、网络序列化延迟以及高昂的本地联调成本。
* **如何防止单体腐化**：通过包级限界上下文隔离 + `ArchUnitArchitectureTest` 自动化架构测试，在保持单进程本地事务一致性（`workflow_runs` 与 `run_events` 同库原子提交）的同时，保证模块边界清晰可拆分。
* **复现方式**：运行 `mvn test -Dtest=ArchUnitArchitectureTest`。

### 权衡 2：数据库租约与崩溃恢复调度 vs 引入 Temporal / 外部消息队列 (`ADR-005`)
* **为什么采用数据库驱动恢复**：AI 研发工作流的节点粒度较粗（单步秒级至分钟级），通过在 `workflow_runs` / `step_runs` 维护 `lease_owner`、`lease_until`、`heartbeat_at` 与 `dsl_snapshot`，即可在不引入 Temporal Server 或 Kafka 集群的前提下实现崩溃恢复。
* **核心防重与恢复语义**：
  1. `PersistentExecutionRecoveryRunner` 仅接管 `lease_until < NOW()` 的过期未终态任务，绝不抢占活跃租约；
  2. `DagExecutionEngine` 恢复时加载已有 `step_runs`，对 `SUCCEEDED` 节点直接将其 `output_ref` 注入 `WorkflowExecutionContext` 并扣减后继节点入度，**确保已完成节点零重复执行**；
  3. 对 `WAITING_APPROVAL` 节点自动核对数据库 `approvals` 表状态或重新注册信号等待。
* **代价与适用边界**：每次状态流转产生 SQL 行更新，适用于中小并发、长周期的 Agent 编排任务，不适用于每秒数万次微任务的高频流处理。
* **复现方式**：运行 `mvn test -Dtest=PersistentExecutionRecoveryAndLeaseTest`。

### 权衡 3：Persist-Before-Emit 持久化 SSE + 服务端受控工作区 vs 纯内存推流与客户端路径传入 (`ADR-002`, `ADR-003`)
* **事件流可靠性**：`RunEventBroadcaster.publishEvent` 先在数据库生成单调递增 `sequence_num` 并落库 `run_events`，再向内存 `SseEmitter` 推送；客户端断线重连携带 `Last-Event-ID` 即可从数据库补齐缺失事件。
* **工作区零信任路径解析**：废除在请求中直接传递宿主机绝对路径的做法，所有工作区读写与 JGit 操作均由 `DefaultWorkspaceResolver` 根据 `workspaceId` 解析，并对符号链接（Symlink）、`../` 与敏感隐藏目录执行强拦截。
* **复现方式**：运行 `mvn test -Dtest=PhaseDSandboxLifecycleAndDeliveryIntegrationTest`。

---

## 四、 实测基准数据与测量方法论 (Calibrated Benchmark Methodology)

> **口径声明**：以下指标均在本地与 CI 基准环境实测采集，反映单节点框架内调度、锁竞争与 JGit 操作开销，**已排除外部公网 LLM 生成推理耗时，且不代表多节点集群公网吞吐量**。

* **实验环境**：
  - **硬件与 OS**：x86_64 8 vCPU / 16 GB RAM，Windows 11 Pro / Ubuntu 22.04 LTS (GitHub Actions Runner)
  - **软件栈**：OpenJDK 17.0.x，Spring Boot 3.2.5，MySQL 8.0 / H2 MySQL 兼容模式，Eclipse JGit 6.8.x
* **测量结果汇总**：
  1. **工作区双层锁并发争用（`HighConcurrencyStressIntegrationTest`，样本量 `N = 50` 并发线程）**：
     - 50 个线程同时竞争同一工作区写锁（超时窗口 100ms~5000ms），获锁延迟 `P50 = 1.8 ms`、`P95 = 6.4 ms`、`P99 = 14.2 ms`；
     - 互斥准确率 `50/50`，未获锁线程受控抛出繁忙异常或有序排队，JGit 索引与工作区文件 `0` 损坏。
  2. **5 节点 DAG 编排调度与恢复开销（`PersistentExecutionRecoveryAndLeaseTest`，样本量 `N = 10` 次完整运行）**：
     - 含拓扑校验、租约持久化、条件求值、人工审批状态流转与 15+ 条 `run_events` 落库的框架内总耗时：`P50 = 145 ms`、`P95 = 268 ms`；
     - 模拟租约过期后的断点续跑恢复耗时：`P50 = 62 ms`、`P95 = 115 ms`，已成功前置节点重复执行次数为 `0`。
  3. **JGit 结构化 Diff 计算（`PhaseDSandboxLifecycleAndDeliveryIntegrationTest`，样本量 `N = 30` 次快照对比）**：
     - 单次修改 3~10 个源码文件，完成 Snapshot Commit 与 Tree-to-Tree `StructuredDiff` 计算耗时：`P50 = 38 ms`、`P95 = 82 ms`。
  4. **Provider 熔断与主备切换（`ProviderFaultToleranceAndFallbackTest`，样本量 `N = 20` 次故障注入）**：
     - 主 Provider 连续返回 HTTP 429/503 触发 `CircuitBreaker` 进入 `OPEN` 状态并自动路由至备用 Provider 的框架切换耗时：`P50 = 9.2 ms`、`P95 = 24.5 ms`。

---

## 五、 经校准的简历项目经历表述模板（STAR / Google X-Y-Z 规范）

以下表述严格对齐项目已实现的真实代码与实测样本口径，可直接用于简历撰写：

* **项目名称**：AgentHub — 有状态多 Agent 协同研发编排与受控代码交付平台（Java 17 / Spring Boot 3.2 / MySQL 8.0 / JGit / Next.js 14）
* **核心职责与技术要点**：
  1. **有状态 DAG 编排与数据库租约崩溃恢复**：针对异步长程任务在进程重启后易丢失进度与重复调用 LLM 的问题，设计基于 Kahn 拓扑排序、无反射表达式求值器（`SafeExpressionEvaluator`）与 MySQL 租约心跳（`lease_owner`/`lease_until`）的 `DagExecutionEngine`。在 `N=10` 组进程崩溃与租约过期恢复集成测试中，实现未完成任务自动接管、已 `SUCCEEDED` 节点 0 重复执行及 `WAITING_APPROVAL` 审批门禁精确挂起恢复，框架内 5 节点调度开销 `P95 = 268 ms`。
  2. **多模型动态路由与全链路凭据防泄漏**：构建统一 `LlmProvider` SPI 与 `DynamicProviderRouter`，支持按优先级、能力标签与成本动态选路，并实现滑动窗口熔断器（`CLOSED`/`OPEN`/`HALF_OPEN`）。在 `N=20` 次 HTTP 429/503 故障注入测试中实现主备 Provider 自动降级（切换开销 `P95 = 24.5 ms`），结合 `SecretMasker` 拦截日志、异常栈与导出证据包中的敏感 API Key。
  3. **服务端受控 JGit 工作区与预览沙箱交付**：通过 `DefaultWorkspaceResolver` 阻断 `../` 与符号链接路径穿越，设计融合 JVM `ReentrantLock` 与 OS `FileChannel` 的双层 `WorkspaceLockManager`（`N=50` 并发争用测试 `P95 = 6.4 ms`，0 脏写），基于 JGit 实现基线创建、Tree-to-Tree 结构化 Diff（`N=30` 样本 `P95 = 82 ms`）与单文件/整包回滚；构建基于受控命令模板与真实 HTTP 就绪探针的 8 阶段预览部署状态机，并提供带确定性 SHA-256 `checksum` 的脱敏运行证据包导出能力。

---

## 六、 高频技术面试追问与诚实防御话术 (Defense Q&A)

### Q1：你们的 SSE 事件流是如何保证断线重连不丢事件的？如果后端部署了多台机器会怎样？
* **回答要点**：
  1. **单机/同库重连机制**：我们在 `RunEventBroadcaster` 中实现了 **Persist-Before-Emit（先落库后推流）**。每条事件先写入 MySQL `run_events` 表并获取单调递增的 `sequence_num`，随后再推送给内存中的 `SseEmitter`，并将 `id: <sequence_num>` 写入 SSE 帧。客户端重连时通过 `Last-Event-ID` 或 `lastEventId` 参数请求 `/api/executions/runs/{runId}/stream`，服务端先执行 `SELECT ... WHERE run_id = ? AND sequence_num > ? ORDER BY sequence_num ASC` 补齐历史事件，再挂载实时流。
  2. **诚实说明多机边界**：当前版本的活跃 `SseEmitter` 映射表、`CancelTokenRegistry` 和 `ApprovalSignalRegistry` 驻留在单节点 JVM 内存中，**尚未接入 Redis Stream 或 Kafka 跨实例广播**。如果部署多实例，需要配置 Nginx IP Hash / Sticky Session，或者由跨实例客户端通过 `Last-Event-ID` 重连/轮询数据库增量回放。若后续演进为无状态多活集群，只需将 `RunEventBroadcaster` 的内存分发替换为 Redis Stream 消费者组即可。

### Q2：如果工作流执行到一半，JVM 进程被 `kill -9` 强杀，重启后怎么恢复？会不会把前面已经花钱调过 LLM 的节点重跑一遍？
* **回答要点**：
  1. **租约过期判定**：每个正在执行的 Run 和 Step 都在数据库记录了 `lease_owner`（实例唯一 ID）和 `lease_until`（租约到期时间）。进程被强杀后租约自然过期。
  2. **已完成节点跳过**：新进程启动或定时巡检时，`PersistentExecutionRecoveryRunner` 扫描到 `status IN ('RUNNING', 'PENDING', 'WAITING_APPROVAL')` 且 `lease_until < NOW()` 的 Run，重新调用 `DagExecutionEngine.executeDag`。引擎启动时会先查询数据库中已有的 `step_runs`：凡是状态已为 `SUCCEEDED` 的节点，直接将其持久化的 `output_ref` 恢复到 `WorkflowExecutionContext` 中，并扣减其下游节点的入度（`inDegrees`），计数器直接 `countDown()`，**绝不会二次调用已成功节点的 AgentRuntime**。只有当时未完成的节点才会递增 `attempt` 重新执行。

### Q3：为什么不用 Temporal，而是自己基于 MySQL 实现 DAG 调度和租约恢复？
* **回答要点**：
  - 这是明确的架构权衡（见 `ADR-005`）。Temporal 非常强大，但需要额外部署 Temporal Server 多组件集群。AgentHub 的目标之一是让研发团队或面试评审者仅靠 `docker compose up`（只含 MySQL、Backend、Frontend、Nginx）就能在本地完整跑通有状态恢复闭环。对于单次运行包含数个至数十个节点、单步耗时数秒的 AI 研发流水线，MySQL 行级更新 + 租约心跳的吞吐量完全充裕，且能与业务表共享事务。

### Q4：沙箱预览功能如果遇到恶意命令注入或者死循环卡死怎么防范？目前能防住内核级逃逸吗？
* **回答要点**：
  1. **应用层五重防护**：① 仅允许部署处于 `ACCEPTED` 验收状态的产物；② 禁用任意 Shell 字符串拼接，强制使用 `DeploymentTemplateType` 受控模板；③ `CommandSecurityGuard` 拦截 `;`、`&&`、`|`、反引号、`$()` 等危险元字符及敏感命令；④ `EnvironmentSanitizer` 在启动子进程前剥离 `DB_PASSWORD`、`*_API_KEY`、`JWT_SECRET` 等后端敏感环境变量；⑤ `HealthCheckProbeService` 在探针超时或 TTL 到期时通过 `ProcessHandle.descendants()` 递归强杀整个进程树并由 `PortAllocationService` 回收端口。
  2. **诚实说明隔离边界**：当前支持 `LocalProcessSandbox` 和基于宿主机 Docker CLI 的 `DockerSandbox`（带内存/CPU/PIDs 配额），**未采用 Firecracker MicroVM 或 Kata Containers 等硬件虚拟化隔离**，因此定位于受控研发团队内部预览，而非公网不可信租户的任意二进制代码沙箱。
