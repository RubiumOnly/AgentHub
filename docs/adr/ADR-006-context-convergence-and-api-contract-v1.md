# ADR-006：Phase 1 生产限界上下文收敛、深模块拆分与 API 契约 v1

- **状态**：已采纳（Accepted）
- **日期**：2026-10-13
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §6 Phase 1（模块收敛与 API/数据契约 v1）

## 1. 背景与问题陈述

在 Phase 0 完成事实基线盘点后，系统仍存在以下结构性演进阻力：

1. **新旧双轨上下文并存**：早期原型包（`com.agenthub.domain.*`、`com.agenthub.adapter.web.*`、`com.agenthub.infrastructure.jgit.WorkspaceLockManager`、`com.agenthub.infrastructure.common.exception.ErrorCode`）与后续模块化单体包并存，且 `SystemHealthController`、`AgentRegistryController` 仍停留在旧 `adapter.web` 包内。
2. **核心应用服务与调度引擎过大**：
   - `ExecutionApplicationService`（948 行）同时承担 Run 创建命令、状态/列表查询、SSE 事件回放流以及 `RunEvidenceBundleView` 证据组装四类职责；
   - `DagExecutionEngine`（919 行）在单类中混杂了拓扑入度计算、工作区/Step 租约续期、CAS 状态转换、Agent/审批节点调用与事件广播五类职责。
3. **控制器层边界泄漏**：`WorkspaceController` 直接注入 `WorkspaceResolver`、`JGitWorkspaceManager` 与 `WorkspaceLockManager` 并操作 `java.nio.file.Path`。
4. **隐式默认种子与超级用户旁路残留**：13 个后端类与 3 个前端文件中残留 `"user-1"`、`"proj-default"`、`"run-default"` 隐式兜底常量；`ResourceAccessGuard` 存在 `user-1` / `system` 硬编码旁路。
5. **前后端契约未版本化且前端单文件过大**：前端 `services/api.ts`（846 行）与 `app/page.tsx`（678 行）职责高度耦合，且仍通过 `/api/im/conversations` 与 `/api/runs/{runId}/stream` 访问旧兼容路由。

## 2. 决策内容

### 2.1 八大生产限界上下文（Bounded Contexts）与错误码区间

正式确立并由 `com.agenthub.shared.contract.BoundedContext` 固化八个核心生产上下文（外加两个隔离协作扩展 `conversation`、`team`）：

| 上下文编码 | 根包路径 | 错误码区间 | 核心聚合 / 职责 | 权威 REST 前缀 |
| --- | --- | --- | --- | --- |
| `identity` | `com.agenthub.identity` | `1000–1999` | `User`、JWT 鉴权、吊销黑名单、SSE 单次票据、`ResourceOwnershipVerifier` | `/api/auth` |
| `project` | `com.agenthub.project` | `2000–3009` | `Project`、`Workspace`、单一 `/api/workspaces/{workspaceId}` 资源族 | `/api/projects`, `/api/workspaces` |
| `audit` | `com.agenthub.audit` | `3010–3999` | `Artifact`、JGit 结构化 Diff、非破坏性 Revert | `/api/audit/artifacts` |
| `agent` | `com.agenthub.agent` | `5000–5999` | `AgentDefinition`、`Provider`、动态路由与熔断、`TokenUsage` | `/api/agents`, `/api/providers`, `/api/token-usages` |
| `orchestration` | `com.agenthub.orchestration` | `6000–6019` | `WorkflowDefinition`、Kahn 拓扑校验、`Approval` 人工门禁 | `/api/workflows`, `/api/approvals` |
| `execution` | `com.agenthub.execution` | `6020–6999` | `WorkflowRun`、`StepRun`、`RunEvent`、SSE 游标流、`RunEvidenceBundleView` | `/api/executions` |
| `sandbox` | `com.agenthub.sandbox` | `7000–7999` | `Deployment`、受控预览模板、端口池、非 root 容器/进程治理 | `/api/deployments` |
| `ops` | `com.agenthub.ops` | `8000–8999` | 系统健康、API v1 契约元数据、OpenAPI 快照、旧接口迁移遥测、冷备恢复 | `/api/system`, `/api/v1/openapi.json` |

### 2.2 重复实现删除与遗留控制器生产环境隔离

1. **物理删除重复实现**：
   - 删除 `com.agenthub.infrastructure.jgit.WorkspaceLockManager`（统一收敛至数据库+内存双层租约实现 `com.agenthub.infrastructure.concurrency.WorkspaceLockManager`）；
   - 删除 `com.agenthub.infrastructure.common.exception.ErrorCode`（统一收敛至 `com.agenthub.shared.exception.ErrorCode`）；
   - 将 `SystemHealthController` 迁入 `com.agenthub.ops.api`，将 `AgentRegistryController` 迁入 `com.agenthub.agent.api`，删除 `com.agenthub.adapter.web` 下的同名旧类。
2. **遗留控制器隔离与 410 迁移拦截**：
   - 对旧兼容控制器（`WorkflowAndDiffController`、`IMController`、`SandboxController`、`RunAliasController`）统一标注 `@Deprecated` 与 `@Profile("!prod")`，确保 `prod` profile 零旧控制器注册；
   - 新增 `LegacyEndpointMigrationFilter` 与 `LegacyEndpointCallTracker`，对 `/api/workspace/*`、`/api/im/*`、`/api/sandbox/*`、`/api/runs/*` 自动附加 `X-Deprecated-Endpoint` 与 `X-Deprecated-Migration-Target` 响应头并统计调用计数；在 `prod` 环境或携带 `X-Enforce-Legacy-Gone: true` 时直接返回 HTTP `410 Gone`。

### 2.3 后端深模块与内部 Seam 拆分

1. **`ExecutionApplicationService`（948 行 ➔ 151 行）**：对外保留单一 `ExecutionApplication` 接口，内部拆分为四个高内聚模块：
   - `RunCommandModule`：Run 启动、幂等键去重、工作流 DSL 快照冻结、取消与状态推进；
   - `RunQueryModule`：Run/StepRun 查询、租户归属校验与分页排序；
   - `RunEventStreamModule`：`RunEvent` 持久化序列查询与 `Last-Event-ID` 游标回放；
   - `RunEvidenceModule`：脱敏 `RunEvidenceBundleView` 组装与确定性 SHA-256 校验和计算。
2. **`DagExecutionEngine`（919 行 ➔ 452 行）**：对外保留单一 `executeDagAsync` 执行入口，内部拆分为五个 seam：
   - `DagTopologyPlanner`：Kahn 拓扑排序、`joinPolicy`（`ALL`/`ANY`/`NONE`）与 `SafeExpressionEvaluator` 条件求值；
   - `DagLeaseExecutor`：工作区锁与 `step_runs` 数据库租约获取、后台心跳续期及释放；
   - `DagStateTransitioner`：`WorkflowRun` / `StepRun` 状态机转换与单 Run 互斥锁；
   - `DagAgentInvoker`：`APPROVAL` 人工门禁挂起唤醒、`AgentRuntime` 执行与 `SUB_WORKFLOW` 递归深度守护；
   - `DagEventRecorder`：`RunEvent` 持久化与 `RunEventBroadcaster` 实时分发。
3. **控制器纯净度与统一归属校验**：
   - `WorkspaceController` 移除对 `java.nio.file.Path`、`JGitWorkspaceManager`、`WorkspaceResolver`、`WorkspaceLockManager` 的全部直接依赖，统一经由 `WorkspaceApplication` 与 `WorkspaceLockStatusView` DTO 交互；
   - 新增 `ResourceOwnershipVerifier`，统一 `Project`、`Workspace`、`Workflow`、`Run`、`Artifact`、`Deployment` 的跨资源归属解析与校验；清除全部 13 个后端类中的 `"user-1"`、`"proj-default"`、`"run-default"` 隐式默认值，将演示配置迁入独立 `application-demo.yml`。

### 2.4 API 契约 v1 与前端模块化拆分

1. **API 契约 v1 规范**：
   - 全局注入 `X-API-Version: v1`、`X-Server-Timezone: UTC` 与 `X-Trace-Id`；
   - 固化 `PageRequestQuery` / `PageResultView<T>` 分页排序契约（`/api/projects/page`、`/api/executions/projects/{projectId}/runs/page`）；
   - 支持 `Idempotency-Key` 请求头（`POST /api/executions/runs`）与弱 `ETag` / `If-None-Match`（`304 Not Modified`）；
   - 输出 `docs/openapi-v1.json` 快照并通过 `GET /api/v1/openapi.json` 与 `GET /api/system/contracts/v1` 暴露。
2. **前端模块化收敛**：
   - 将 `services/api.ts`（846 行 ➔ 98 行）拆分为 `services/api/{errors,auth,projects,workspaces,workflows,runs,approvals,artifacts,deployments,stream}.ts` 十个模块，内置统一错误模型 `ApiError`、`ETag` 304 缓存、幂等 GET 重试、`AbortController` 请求取消及空状态辅助函数；
   - 将 `app/page.tsx`（678 行 ➔ 346 行）拆分为 `ProjectShell`、`RunWorkspacePanel`、`ReviewSidebar`、`DeploymentPanel` 四个组件；全量切换至 `/api/conversations` 与 `/api/executions/runs/{runId}/stream`，前端源码 `"proj-default"`、`/api/im/`、`/api/runs/`、`/api/sandbox/` 全部清零。

## 3. 后果与门禁验证

- **正面影响**：消除控制器跨层直连与隐式默认 ID 污染；前后端围绕单一 OpenAPI v1 契约演进；大类职责清晰解耦。
- **兼容性与回滚**：Flyway `V1`～`V12` 表结构保持不变；旧 REST 路由在非生产 profile 下通过 `@Profile("!prod")` 保留短期兼容并记录遥测，生产 profile 彻底隔离。
