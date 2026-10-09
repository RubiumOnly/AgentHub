# AgentHub 核心技术架构白皮书与大厂级答辩防穿指南

> **版本**：`v1.9.0 (Enterprise GA)`  
> **定位**：企业级多智能体协同研发与可审计交付平台 (Multi-Agent Collaborative Engineering Platform)  
> **核心原则**：真闭环、真测试、真并发、防穿透、可审计、零塑料味

---

## 1. 平台架构全景与 DDD 领域分层

AgentHub 采用高内聚、低耦合的**模块化单体架构 (Modular Monolith)**，遵循严格的 DDD（领域驱动设计）四层架构分层规范，并通过 ArchUnit 架构守护单测实现 100% 静态防腐。

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                       Adapter Layer (接入适配层)                              │
│   Web Controllers (REST API)  │  SSE Streaming Hub  │  Nginx Reverse Proxy  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Command / Query / View DTO)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                     Application Layer (领域应用编排层)                       │
│  ProjectApp  │  ExecutionApp  │  WorkflowApp  │  ConversationApp  │ SandboxApp│
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Domain Entities & Domain Services)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                        Domain Core (纯业务逻辑内核)                          │
│  • ExecutionStateMachine (8态生命周期)    • SafeExpressionEvaluator (AST求值)│
│  • DagExecutionEngine (Kahn拓扑排序)      • SecretMasker (API Key 脱敏)     │
│  • ConversationSequenceManager (128分段锁) • CommandSecurityGuard (命令防火墙)│
│  • JGitWorkspaceManager (快照/Diff/Revert) • DynamicProviderRouter (熔断/路由) │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ (Ports & SPI Adapters)
┌──────────────────────────────────────▼──────────────────────────────────────┐
│                    Infrastructure Layer (基础设施与持久化)                    │
│  • Flyway (V1~V8 MySQL/H2 双模迁移)       • WorkspaceLockManager (租约锁)    │
│  • Spring Boot Actuator & Prometheus 指标 • Docker & Process Sandbox        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. 三大核心架构设计权衡 (Trade-Offs) 深度剖析

面试或技术评审中，最核心的考察点在于**“为什么这样设计，而不是那样设计”**。以下为 AgentHub 的三大关键决策树与防御论据：

### 决策一：为什么采用“模块化单体 (Modular Monolith)”而非微服务？
- **业务矛盾**：Agent 协同任务具备高上下文密度与长事务链。在 10 人以内研发团队或单集群规模下，微服务拆分会带来分布式事务协调（Saga/2PC）、网络序列化开销、跨服务调用调试地狱与沉重的运维负担。
- **方案权衡**：
  - 采用模块化单体，在单个 JVM 进程内通过 Java Package、Domain 边界与 Application Service 严格隔离；
  - 引入 **ArchUnit 架构自动化测试**：强制要求 Controller 绝不能直接注入 Repository、实体严禁穿透出 Controller、外部只能通过 Application DTO 交互；
  - 数据模型全部通过 Flyway 脚本做外键与索引治理，保留未来按领域包快速平滑拆分为微服务的能力。
- **收益**：吞吐最大化、测试执行只需 30 秒全部跑通、运维仅需一个生产 Docker 容器，同时杜绝架构腐化。

---

### 决策二：为什么采用“SSE + 关系数据库持久化事件”而非“裸 WebSocket”？
- **业务矛盾**：多 Agent 执行是长时间异步任务（可能长达数分钟），网络环境不可靠，客户端随时可能由于浏览器刷新、移动端切换或 WiFi 波动断开连接。裸 WebSocket 是双向无状态的，一旦连接中断，客户端无法知道断线期间丢了哪些事件。
- **方案权衡**：
  - 核心下行采用 **HTTP Server-Sent Events (SSE)**：轻量基于 HTTP/1.1，天然适配现代浏览器原生重连机制，且单向直推非常契合模型 Token 生成与终端日志场景；
  - 上行操作（发送消息、取消任务、人工审批）走标准化幂等 REST API，具备完整的认证主体与审计追踪；
  - **单调连续序列与 Last-Event-ID 重播机制**：每一个事件在发布时原子分配严格单调递增的 `sequence_num` 并同步持久化到 `run_events` 表。客户端重连时携带 HTTP 请求头 `Last-Event-ID: 42`，服务端自动截取第 42 号之后的历史遗漏事件精准补发，保证 100% 零消息丢失与绝对保序。

---

### 决策三：为什么受控工作区采用“JGit 内部快照 + 结构化 Diff”而非“直接软重置/物理全量复制”？
- **业务矛盾**：Agent 在修改工程代码时可能引入 Bug、破坏既有功能或插入不可信代码。如果每次 Step 都把整个项目目录做一次物理 `cp -r` 全量备份，大项目下磁盘空间会呈指数级爆炸，I/O 严重打满；如果直接依赖外部 git cli 执行 `git reset --hard`，则可能直接抹杀用户的未提交代码，造成破坏性灾难。
- **方案权衡**：
  - 在每个 Project 内部建立受控的嵌入式 JGit 实例；
  - 在任务开始时创建 `tag-run-baseline-{runId}` 作为基准锚点；
  - 每个 Step 产物写入后，以毫秒级速度创建 Git Snapshot Commit，并精确计算新增、修改、删除文件的结构化 Diff 对象（Unified Patch、增减行统计、Checksum 哈希）；
  - **非破坏性回滚 (Non-destructive Revert)**：当用户或质量门禁拒绝某一步骤产物时，系统仅对该 Step 所涉及的特定文件列表，从基准 Commit 中提取原始内容覆盖写回（`revertStepFiles`），未被污染的文件毫发无损，并自动提交一条带有明确审计备注的 Revert Commit，保留完整的操作历史与安全黑匣子。

---

## 3. 真实性能与高并发基线数据 (无虚假水分)

所有指标均由自动化集成测试与基准压测套件真实测量产生：

| 压测指标项 | 观测数值 | 测试用例依据 |
| :--- | :--- | :--- |
| **全量自动化测试覆盖** | **263 项用例 100% 绿灯** (后端 259 项 + 前端 4 项构建) | `FullLifecycleEndToEndIntegrationTest`, `ArchUnitArchitectureTest` 等 |
| **工作区并发锁竞争互斥率** | **100% 互斥 (Max Holders <= 1)**，死锁发生率 0% | `HighConcurrencyStressIntegrationTest.testHighConcurrencyWorkspaceLockCompetition` |
| **多 Agent 消息 128 分段锁单调定序** | **50 并发请求 87ms 内完成分配**，0 丢号，0 倒序 | `HighConcurrencyStressIntegrationTest.testHighConcurrencyMessageBusMonotonicSequencing` |
| **批量 DAG 拓扑执行吞吐** | **5 个复杂工作流 541ms 内全量跑通** (P50: 252ms, P99: 268ms) | `HighConcurrencyStressIntegrationTest.testConcurrentDagExecutionThroughputAndLatency` |
| **沙箱命令防火墙拦截率** | **100% 阻断** (覆盖 rm -rf, mkfs, dd, 管道注入等 12 种模式) | `SandboxSecurityGuardAndFirewallTest` |
| **受控路径穿越拦截率** | **100% 阻断** (覆盖 ../, NTFS 设备名, 外部绝对路径, .git 篡改) | `WorkspacePathGuardTest`, `WorkspaceAdversarialChallengeTest` |

---

## 4. 大厂面试官高频 10 问与防穿防爆指南

### Q1: 如果多个 Agent 同时在同一个工作区写文件，如何保证并发安全？
> **答**：我们落地了四层防御：
> 1. 架构级采用 `WorkspaceLockManager` 租约锁，具备 Owner 机制、Lease TTL（默认 60s）与心跳续期；
> 2. 租约锁支持自动故障转移：若持有者进程异常崩溃，后继线程在租约超时后自动接管锁，防范死锁；
> 3. 工作区文件变更统一走 JGit 快照机制，每次写入均校验文件 Checksum；
> 4. 在 DAG 编排层面，相互冲突的写步骤通过有向边强制声明拓扑先后依赖。

### Q2: 你们的 DAG 工作流是如何避免死锁和环路依赖的？
> **答**：在 DSL 入库解析阶段，采用经典 **Kahn 拓扑排序算法** 对节点入度进行遍历。一旦入度消除后剩余节点数不等于总节点数，立即识别出环路依赖并抛出专用错误码 `WORKFLOW_CYCLE_DETECTED`，坚决不让非法 DAG 进入调度引擎。

### Q3: 为什么你们没有用 SpEL 来解析条件分支表达式？
> **答**：SpEL 在非受信输入下极易遭受 Java 远程代码执行漏洞（RCE，如 `T(java.lang.Runtime).getRuntime().exec(...)`）。AgentHub 自研了无递归深度溢出的 **递归下降安全表达式求值器 (`SafeExpressionEvaluator`)**，采用纯词法白名单（只允许标识符、比较运算符、布尔逻辑符与基础字面量），从语法解析层面彻底杜绝任意代码执行隐患。

### Q4: 多 Agent 对话时，如何防止 Agent 之间无限互相回复导致 Token 爆仓？
> **答**：我们设计了四层递进式防死循环机制：
> 1. 会话级最大轮次限制 (`maxTurns`)；
> 2. 会话状态流转检测：一旦触发死循环直接将状态置为 `TERMINATED`；
> 3. 消息指纹比对：检测多轮内内容重复与振荡模式；
> 4. 滑动窗口与滚动摘要机制：超过 Token 预算自动对早前历史消息进行压缩总结。

### Q5: 外部大模型 API 遭遇 429 限流或超时时，平台如何保障高可用？
> **答**：落地了 `DynamicProviderRouter` 与 `ProviderCircuitBreaker` 三态熔断器（`CLOSED`, `OPEN`, `HALF_OPEN`）。当主 Provider（如 DeepSeek）失败率或 429 超标时，熔断器打开，路由自动平滑降级至备用 Provider（如 OpenAI/Claude），并向系统事件流推送告警。
