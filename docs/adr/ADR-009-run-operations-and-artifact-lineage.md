# ADR-009：Phase 4 Run Operations、人工决策与产物血缘可靠性

- **状态**：已采纳（Accepted）
- **日期**：2026-10-14
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §7 Phase 4（Run Operations、人工决策与长上下文可靠性）

## 1. 背景与问题陈述

在 Phase 3 完成 Agent 工作单元、DAG Kahn 拓扑批次编排与 Orchestrator 结构化计划之后，AgentHub 具备了受控规划与多智能体分阶段执行能力。然而，在实际长时间异步运行与复杂协作中，仍面临以下关键可靠性与操作性挑战：

1. **状态机割裂与防循环盲退保障不足**：`WorkflowRun`、`StepRun`、`Approval` 和 `Artifact` 实体状态流转散落在各个模块中，缺乏单一、强契约的全局状态图谱校验（Unified State Graph）；在异步等待、人工审批与超时轮询中容易出现盲退与状态不一致。
2. **重试机制粗放与非幂等重入风险**：网络闪断（SocketTimeout、503）、限流（429）、审批挂起（APPROVAL_REQUIRED）与致命代码错误（NullPointerException、DSL 语法异常）缺乏细粒度分类归因，容易发生盲目重试或将不可恢复的错误陷入无限死循环。
3. **SSE 实时流存在事务幻象与断线重放丢失**：原有事件发布直接在方法内触发，若后续数据库事务异常回滚，前端已接收到“执行成功”或“步骤已推进”的虚假事件；客户端断线重连后缺乏严格的 `Last-Event-ID` 增量重放保障。
4. **长上下文记忆膨胀与关键不变量丢失**：在多轮交互与深层 DAG 执行中，简单的字符串裁剪容易将核心系统规约（如 DDD 四层架构、分布式锁看门狗）、人工审批决议、致命错误堆栈与文件修改收据直接抹除，造成认知漂移与幻觉。
5. **产物血缘模糊与破坏性回滚风险**：缺乏从 `WorkflowRun -> StepRun -> Artifact -> JGit Commit Hash -> SHA-256 Checksums -> Approval Decision` 的全生命周期血缘穿透；当用户需要驳回某一阶段生成的代码时，传统的 `git reset --hard` 会粗暴覆盖工作区中其他不相关的未提交修改。
6. **运维透视黑盒**：缺少高内聚的统一运维查询入口，前端看板与操作员无法一揽子获知当前 Run 的单一状态、待决决策项、分层记忆摘要、分布式租约健康度与下一步调度建议。

## 2. 决策内容

### 2.1 单一统一状态图（UnifiedExecutionStateGraph）与调度建议

1. **全状态跃迁矩阵**：在领域层建立 `UnifiedExecutionStateGraph`，穷举 `WorkflowRunStatus`（8 种状态）与 `StepRunStatus`（6 种状态）之间的合法跃迁矩阵。终态（`SUCCEEDED`、`CANCELLED`）严禁跃迁回运行态，非法流转即时抛出 `IllegalStateException`。
2. **跨实体一致性校验（`isRunStepConsistent`）**：在工作流标记成功前，强校验所有步骤必须全部为终态且无失败/取消；在 `WAITING_APPROVAL` 时强校验必须存在审批挂起节点。
3. **结构化下一步决策生成（`determineNextAction`）**：针对 `WAITING_APPROVAL`（待审批人准入）、`PAUSED`（待操作员恢复或中止）、`RUNNING`（活跃步骤编排执行中）、`FAILED`（检查日志或重试步骤）生成确定性的结构化指导建议与操作员待办事项（`PendingDecisionItem`）。

### 2.2 At-Least-Once 语义与三级异常归因（ExecutionErrorClassifier）

1. **三级异常分类器**：
   - `RETRYABLE_TRANSIENT`：捕获 `TimeoutException`、`SocketTimeoutException`、429 Rate Limit、连接重置与 5xx 临时服务端异常，标记 `isAutoRetryable=true`，触发指数退避重试；
   - `APPROVAL_REQUIRED`：捕获未经授权的高影响工具访问、预算配额超额与审批挂起，标记 `isAutoRetryable=false`，转入人工确认流程；
   - `FATAL_NON_RETRYABLE`：捕获业务异常、空指针、DSL 契约损坏与语法错误，立即标记失败阻断重试。
2. **幂等执行凭证防重入**：`startRun` 严格基于 `idempotencyKey` 进行唯一索引过滤，重复触发直接返回已创建的 `WorkflowRunView`，杜绝产生孤儿运行实例。

### 2.3 事务性发件箱（Transactional Outbox）与严格单调序列

1. **`afterCommit` 延期广播机制**：在 `RunEventBroadcaster` 中利用 Spring `TransactionSynchronizationManager.registerSynchronization`，确保仅当包含事件保存的数据库事务成功提交（`afterCommit`）后才向激活的 SSE Emitter 派发实时事件，彻底消除事务回滚造成的幻象事件。
2. **单调自增序列号与断线恢复**：每条 `RunEventEntity` 具备全局单调递增的 `sequenceNum`。前端携带 `Last-Event-ID` 重连时，通过 `listEvents(runId, afterSeq)` 无损重放遗漏的历史事件。

### 2.4 三层拓扑分层上下文记忆与不变量保留压缩

1. **三层记忆模型（`HierarchicalContextMemory`）**：
   - **Tier 1 (ProjectMemory)**：持有项目规约（DDD 原则、测试防御门禁）、Git 基线与 AOCI 认知对齐状态；
   - **Tier 2 (RunMemory)**：持有 Run 目标、已确权计划（OrchestrationPlan）与 Token 预算配额；
   - **Tier 3 (StepMemoryItem)**：持有每个步骤的调用工具清单、改动文件收据、审批决定与错误摘要。
2. **不变量保留压缩器（`ContextMemoryCompressor`）**：当预算超出时，优先将历史成功步骤的详细日志脱水为紧凑结构化收据，**绝对保留项目核心规约、改动文件路径、人工审批记录与失败错误详情**。
3. **认知与未提交收据校验（`verifyCognitionAndReceipt`）**：通过 JGit 真实校验工作区是否存在未被记录的越权文件漂移（`DRIFTED` vs `CLEAN`）。

### 2.5 产物全生命周期血缘图谱与非破坏性 JGit 审查回滚

1. **血缘链路贯穿（`ArtifactLineageView`）**：
   - 实现 `GET /api/audit/artifacts/lineage/{runId}`，聚合生成完整的拓扑血缘：`Run -> StepRun -> Artifact -> JGit Commit Hash -> Checksums -> Approval Decision`。
2. **JGit 行级结构化 Diff 审查（`GET /api/audit/artifacts/{artifactId}/diff`）**：
   - 精确计算该快照相对于基线（或前一快照）的新增行（`addedLines`）、删除行（`deletedLines`）、差异文本（`diffText`）、重命名、二进制与冲突标记。
3. **非破坏性精准回滚（`revertArtifact`）**：
   - 提取该产物所涉及的特定文件集，仅将这组文件还原至基线版本（或删除该步新增的文件），并提交专属回滚 commit；
   - **工作区中由用户或其它步骤创建的独立文件完全保留**，绝不执行毁灭性的 `git reset --hard`；
   - 回滚后同步将产物状态标记为 `REVERTED`，并在 `run_events` 中记录不可篡改的 `ARTIFACT_REVERTED` 审计条目。

### 2.6 运行运维控制台与全景透视接口

1. **统一后端运维端点**：提供 `GET /api/executions/runs/{runId}/operations`，返回包含工作流、步骤列表、单一状态图决策、待办决策清单、受控工具副作用收据、分层记忆摘要、血缘节点与分布式租约状态的综合模型。
2. **现代化前端交互落地**：
   - `RunOperationsPanel.tsx`：采用 Bento Grid 质感，集成统一状态图徽章、待处理审批快速响应卡、三层记忆仪表盘、租约与重试归因诊断及产物血缘列表；
   - `StructuredDiffReviewModal.tsx`：提供类 GitHub 的高质感代码审查模态框，支持逐文件审查、语法高亮差异、采纳（Accept）、驳回（Reject）与无损回滚（Revert to Baseline）。

## 3. 架构影响与收益

1. **零新增表与零架构债务**：全面复用现有 22 张 Flyway 核心表（`run_events`、`workflow_runs`、`step_runs`、`artifacts`、`approvals`、`workspace_locks`），没有引入新迁移脚本或破坏既有契约。
2. **状态确定性与跨平台防竞态**：所有后台异步执行与状态变更均受统一状态图与分布式租约保护，测试套件具备完全的 `@AfterEach` 级联清理，杜绝异步线程泄漏与脏数据污染。
3. **全量门禁全绿通过**：
   - 后端 57 个测试类、339 个测试用例（含新增的 `Phase4RunOperationsAndArtifactLineageTest` 7 个专项测试）全绿通过；
   - 前端 TypeScript 编译与 Next.js 生产打包（`npm run build`）零警告零错误通过；
   - 生产环境编排配置文件（`docker-compose.prod.yml`）语法校验完全合规。
