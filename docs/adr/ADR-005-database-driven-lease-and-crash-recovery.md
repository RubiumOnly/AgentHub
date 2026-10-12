# ADR-005: 采用数据库驱动租约与崩溃恢复调度 (Database-Driven Lease & Crash Recovery Scheduling)

* **状态 (Status)**: 已采纳 (Accepted)
* **制定者 (Deciders)**: AgentHub 核心架构组
* **日期 (Date)**: 2026-10-12
* **技术背景 (Technical Story)**: `docs/AGENTHUB_NEXT_LONG_TERM_PLAN.md` 阶段 C（有状态工作流持久化恢复和并发语义）与阶段 F（关键架构权衡校准）

---

## 一、 背景与问题陈述 (Context and Problem Statement)

在 AgentHub 的 DAG 编排执行过程中，单次工作流执行（WorkflowRun）往往跨越数十秒乃至数分钟，包含多个并行或串行的 Agent 节点（StepRun）以及耗时不确定的人工审批门禁（Approval Gate）。

在早期原型中，DAG 拓扑推进、已完成节点集合、上下文传递（`WorkflowExecutionContext`）以及审批阻塞信号（`CompletableFuture<ApprovalDecisionResult>`）均驻留在单次 JVM 进程内存与线程池闭包中。一旦后端服务发生重启、滚动发布或进程崩溃：
1. 处于 `RUNNING` 或 `WAITING_APPROVAL` 状态的任务会永远停滞为“幽灵运行态”；
2. 若简单重新触发整条工作流，原先已执行成功的节点会被重复调用，不仅浪费昂贵的 LLM Token，还可能重复修改 JGit 工作区并破坏幂等性；
3. 若两个调度器线程或恢复协程同时认领同一条未完成 Run，缺乏租约互斥会导致并发双跑（Split-Brain Execution）。

我们需要在保持模块化单体（Modular Monolith）轻量部署特性的前提下，实现可验证的断点续跑、审批重挂与租约防重机制。

---

## 二、 决策动因 (Decision Drivers)

1. **进程重启后的断点续跑（Crash Recovery）**：服务重启后必须能自动扫描或按需恢复未终态的 `workflow_runs`（`PENDING`、`RUNNING`、`WAITING_APPROVAL`），跳过已处于 `SUCCEEDED` / `SKIPPED` 的 `step_runs`，从断点继续推进后续拓扑。
2. **租约互斥与防双跑（Lease Mutual Exclusion）**：必须保证同一时刻只有一个执行器持有某个 Run/Step 的有效租约（`lease_owner` + `lease_until`），活跃租约未被抢占，过期租约可被安全接管。
3. **运维复杂度受控**：避免为了 DAG 状态恢复而强制引入 Temporal Cluster、ZooKeeper 或重型分布式消息队列，确保单机 `docker compose up` 即可完整复现崩溃恢复全流程。
4. **诚实声明能力边界**：清晰界定数据库轮询 + 租约方案在吞吐量、锁粒度与跨实例信号唤醒上的工程折中。

---

## 三、 备选方案 (Considered Options)

1. **方案 A：引入外部分布式工作流引擎（Temporal / Camunda / Cadence）**
   - 将 DAG 调度与状态持久化完全托管给外部 Temporal Server 与其专用 Cassandra/PostgreSQL 存储。
2. **方案 B：引入 Redis 分布式锁（Redisson）+ Kafka 事件驱动状态机**
   - 使用 Redisson 看门狗锁控制租约，通过 Kafka Topic 驱动每个 Step 节点的状态流转。
3. **方案 C：基于关系型数据库的持久化快照、租约心跳与启动/定时恢复器（Database-Driven Lease & Crash Recovery，选定）**
   - 在 `workflow_runs` 与 `step_runs` 表中维护 `dsl_snapshot`、`inputs_json`、`outputs_json`、`lease_owner`、`lease_until`、`heartbeat_at` 与 `attempt` 字段（见 `V10__phase_c_scheduling_leases_and_recovery.sql`）；
   - 由 `DagExecutionEngine` 在节点推进时持久化输入/输出快照并续期租约，由 `PersistentExecutionRecoveryRunner` 在进程启动与定时巡检时接管租约已过期的未完成 Run，重建 `WorkflowExecutionContext` 与 `ApprovalSignalRegistry` 监听。

---

## 四、 决策结论 (Decision Outcome)

**采纳方案 C：基于关系型数据库的持久化快照、租约心跳与崩溃恢复调度**。

### 核心实施机制

1. **快照与上下文持久化（Snapshot & Context Persistence）**：
   - 启动执行时，将校验通过的 `WorkflowDsl` 序列化写入 `workflow_runs.dsl_snapshot`；
   - 每个 `StepRunEntity` 执行前写入 `inputs_json`，执行成功后将输出写入 `output_ref` 与 `outputs_json`，并清空节点级租约。
2. **租约与心跳防重（Lease & Heartbeat Guard）**：
   - `DagExecutionEngine` 为每个进程实例生成唯一 `INSTANCE_WORKER_ID`，认领 Run 时写入 `lease_owner` 与 `lease_until`；
   - 若另一个恢复线程或实例检测到 `lease_owner != INSTANCE_WORKER_ID` 且 `lease_until > NOW()`，立即放弃并发认领，杜绝活跃任务被双跑。
3. **幂等恢复与已完成节点跳过（Idempotent Recovery & Completed-Step Skipping）**：
   - `PersistentExecutionRecoveryRunner` 扫描租约已过期（`lease_until < NOW()` 或 `lease_owner IS NULL`）且处于 `RUNNING` / `PENDING` / `WAITING_APPROVAL` 的 Run；
   - 恢复执行时，`DagExecutionEngine` 读取已有 `step_runs`：对已 `SUCCEEDED` 的节点直接将其 `output_ref` 重新注入 `WorkflowExecutionContext` 并扣减后继节点入度（`inDegrees`），**绝不二次执行已成功节点**；
   - 对处于 `WAITING_APPROVAL` 的节点，若数据库审批记录在停机期间已变为 `APPROVED` / `REJECTED`，立即按决策结果推进；若仍为 `PENDING`，则重新挂载阻塞等待并轮询数据库状态兜底。

### 正面收益 (Positive Consequences)

* **零额外中间件依赖**：仅依赖 MySQL 8.0（测试支持 H2 MySQL 模式），单套数据库事务即可保证业务状态、事件序号与租约字段的一致性。
* **断点续跑与审批恢复 100% 可在集成测试中复现**：通过 `PersistentExecutionRecoveryAndLeaseTest` 直接模拟租约过期与引擎重启，验证已完成节点零重复调用、审批门禁恢复放行与活跃租约防抢占。

### 负面代价与已知边界 (Negative Consequences & Known Boundaries)

* **吞吐上限受限于数据库行锁与轮询周期**：相比 Temporal 或内存 Actor 模型，每次节点状态流转与租约续期均产生 SQL `UPDATE`，适合中低频、长耗时的 AI Agent 研发流水线（单次 Run 数秒至数分钟、单机数十并发 Run），不适用于每秒数万次微任务调度的高频流处理场景。
* **跨节点即时唤醒边界**：`CancelTokenRegistry` 与 `ApprovalSignalRegistry` 的即时信号通知驻留在单机内存中；在跨进程恢复或多实例场景下，审批决策与恢复依赖数据库状态轮询（1 秒级延迟）与租约过期巡检，而非毫秒级跨节点 RPC 广播。

---

## 五、 关联验证与代码锚点 (Verification & Code Anchors)

* 迁移脚本：`agenthub-backend/src/main/resources/db/migration/V10__phase_c_scheduling_leases_and_recovery.sql`
* 核心实现：
  - `com.agenthub.orchestration.scheduler.DagExecutionEngine`
  - `com.agenthub.execution.scheduler.PersistentExecutionRecoveryRunner`
  - `com.agenthub.infrastructure.concurrency.WorkspaceLockManager`
* 自动化验证：`com.agenthub.PersistentExecutionRecoveryAndLeaseTest`、`com.agenthub.HighConcurrencyStressIntegrationTest`
