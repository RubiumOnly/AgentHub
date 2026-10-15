# AgentHub API 接口与事件流规范

本文档定义 AgentHub 核心后端服务的 RESTful 接口契约、身份鉴权机制与 Server-Sent Events (SSE) 事件流协议。

---

## 一、 统一响应结构、API 契约 v1 与链路追踪

所有 RESTful 接口均返回标准统一响应体 `Result<T>`（时区固定为 `UTC`），并在响应 Header 中附带契约版本与追踪标识：
- 响应头：`X-API-Version: v1`, `X-Server-Timezone: UTC`, `X-Trace-Id: <uuid>`, `X-Request-ID: <uuid>`, `X-Correlation-ID: <uuid>`
- 乐观并发与条件缓存：`GET /api/projects/{id}` 与 `GET /api/executions/runs/{runId}` 返回弱校验 `ETag: W/"v1-..."`，支持客户端携带 `If-None-Match` 返回 `304 Not Modified`；
- 幂等键契约：`POST /api/executions/runs` 支持通过 HTTP Header `Idempotency-Key`（或请求体 `idempotencyKey`）执行幂等去重；
- 统一分页与排序契约：`GET /api/projects/page` 与 `GET /api/executions/projects/{projectId}/runs/page` 统一接收 `page`（默认 `1`）、`size`（默认 `20`，最大 `100`）、`sortBy`（默认 `createdAt`）、`sortDir`（`ASC` / `DESC`），返回 `Result<PageResultView<T>>`；
- 契约元数据与 OpenAPI 快照：`GET /api/system/contracts/v1`、`GET /api/v1/openapi.json`（静态快照见 [`docs/openapi-v1.json`](openapi-v1.json)）与遗留接口遥测 `GET /api/system/legacy-endpoints`。

```json
{
  "code": 0,
  "message": "Operation Successful",
  "data": { ... },
  "timestamp": 1791287780319,
  "requestId": "req-9843c9b7-08b5-4df3"
}
```

- `code == 0`：操作成功；
- `code != 0`：业务或系统异常，`message` 包含具体可读的中文异常原因，`requestId` 用于全链路日志排查。

### 核心业务错误码字典 (ErrorCode)
- `1001` UNAUTHORIZED：未提供认证令牌或令牌已失效；
- `1002` FORBIDDEN：无权访问或修改此资源（跨租户/跨项目越权拦截）；
- `1003` INVALID_CREDENTIALS：用户名或密码错误；
- `1004` USER_ALREADY_EXISTS：该邮箱已被注册；
- `2001` PROJECT_NOT_FOUND：目标项目不存在；
- `3001` WORKSPACE_NOT_FOUND：目标工作区不存在；
- `3002` WORKSPACE_LOCKED：工作区当前已被并发任务或租约锁定；
- `3003` WORKSPACE_PATH_INVALID：工作区路径格式非法、包含空字节或命中系统保留设备名；
- `3004` DIRECTORY_TRAVERSAL_FORBIDDEN：工作区路径穿越违规（含符号链接越权）；
- `3005` GIT_DIRECTORY_MODIFICATION_FORBIDDEN：禁止直接读写 `.git` 内部文件；
- `2001` PROJECT_NOT_FOUND：目标研发项目不存在；
- `2002` PROJECT_ACCESS_DENIED：非项目所有者，拒绝越权访问；
- `2003` ONBOARDING_FAILED：项目接入向导步骤执行失败；
- `2004` ONBOARDING_SESSION_NOT_FOUND：项目接入会话记录不存在；
- `2005` CONTEXT_SOURCE_NOT_FOUND：版本化上下文源记录不存在；
- `3001` WORKSPACE_NOT_FOUND：目标受控代码工作区不存在；
- `3002` WORKSPACE_LOCKED：工作区当前正处于锁占用状态；
- `3003` WORKSPACE_PATH_INVALID：非法的工作区相对路径格式或特殊字符；
- `3004` WORKSPACE_TRAVERSAL_DENIED：尝试进行跨工作区目录穿越或访问宿主绝对路径；
- `3005` WORKSPACE_GIT_ACCESS_DENIED：禁止直接读写受控工作区内部 `.git` 元数据；
- `3006` WORKSPACE_GIT_ERROR：JGit 底层操作执行异常；
- `3007` WORKSPACE_FILE_TOO_LARGE：文件体积超过单文件上限（10MB 写入 / 2MB 预览）；
- `3008` WORKSPACE_BINARY_PREVIEW_DENIED：二进制文件无法作为纯文本预览；
- `3009` WORKSPACE_RESERVED_DEVICE_DENIED：命中了系统保留设备名；
- `3010` ARTIFACT_NOT_FOUND：目标产物记录不存在；
- `3011` ARTIFACT_REVIEW_INVALID：非法的产物审查或重复回滚操作；
- `3012` BLOCKED_POLICY：触发受控工作区安全配额或网络策略熔断阻断；
- `4001` CONVERSATION_NOT_FOUND：协同会话不存在；
- `4003` CONVERSATION_PERMISSION_DENIED：非当前会话参与者；
- `4004` MESSAGE_NOT_FOUND：指定消息记录不存在；
- `4005` TEAM_NOT_FOUND：协同团队不存在；
- `4006` TEAM_MEMBER_NOT_FOUND：团队成员不存在；
- `4007` LOOP_DETECTED：检测到死循环或短周期振荡碰撞，已触发熔断保护；
- `4008` MAX_TURNS_EXCEEDED：达到最大协作轮次阈值；
- `4009` MESSAGE_SEQUENCE_CONFLICT：消息定序版本冲突；
- `4010` MESSAGE_RECIPIENT_NOT_FOUND：点对点私聊目标接收者不存在；
- `5001` AGENT_NOT_FOUND：智能体定义或实例不存在；
- `5005` PROVIDER_NOT_FOUND：大模型或 CLI 提供商不存在；
- `5006` PROVIDER_UNAVAILABLE：提供商不可用或当前处于熔断状态；
- `5007` PROVIDER_RATE_LIMIT：大模型 Provider 命中 429 速率限制；
- `5008` PROVIDER_AUTH_FAILED：大模型 Provider 凭证鉴权失败；
- `5009` NO_AVAILABLE_PROVIDER：无可满足路由与能力约束的可用 Provider；
- `6001` WORKFLOW_INVALID：工作流 DSL 拓扑非法（如存在环路、孤立节点或无效引用）；
- `6002` RUN_NOT_FOUND：执行实例不存在；
- `7001` DEPLOYMENT_NOT_FOUND：目标部署记录不存在；
- `7002` SANDBOX_COMMAND_BLOCKED：命令被安全防火墙阻断（命中了高危破坏模式或非白名单程序）；
- `7003` SANDBOX_TIMEOUT：沙箱命令执行超时（已被看门狗强平，退出码 137）；
- `7004` SANDBOX_OUTPUT_TRUNCATED：沙箱输出超出缓冲区配额上限并被截断；
- `7005` SANDBOX_EXECUTION_FAILED：沙箱子进程启动或执行异常；
- `7006` DEPLOYMENT_PORT_EXHAUSTED：受控端口池已耗尽；
- `7007` DEPLOYMENT_BUILD_FAILED：部署构建阶段失败；
- `7008` DEPLOYMENT_HEALTH_CHECK_FAILED：服务健康检查探测超时或未响应；
- `7009` DEPLOYMENT_INVALID_STATUS：非法的部署状态机流转；
- `9001` INTERNAL_ERROR：系统内部未捕获异常。

---

## 二、 核心 RESTful 端点契约

### 1. 身份认证与用户体系 (Identity & Auth)
所有受保护接口通过请求头传递 `Authorization: Bearer <token>`：
- `POST /api/auth/register`：用户注册
  - Request: `{"username": "alice", "email": "alice@agenthub.com", "password": "Password123!"}`
  - Response: `Result<AuthTokenView>` (`{"token": "...", "tokenType": "Bearer", "expiresIn": 86400, "user": {...}}`)
- `POST /api/auth/login`：用户登录
  - Request: `{"email": "alice@agenthub.com", "password": "Password123!"}`
  - Response: `Result<AuthTokenView>`
- `POST /api/auth/refresh`：刷新访问令牌并废弃旧令牌
  - Header: `Authorization: Bearer <token>`
  - Response: `Result<AuthTokenView>`
- `POST /api/auth/logout`：退出注销并将当前令牌列入废止名单
  - Header: `Authorization: Bearer <token>`
  - Response: `Result<Void>`
- `GET /api/auth/me`：获取当前登录用户的个人信息
  - Response: `Result<UserView>` (`{"id": "...", "username": "...", "email": "...", "status": "ACTIVE"}`)
- `POST /api/auth/stream-ticket`：签发单次有效、60 秒过期的 SSE 流式订阅票据（避免在 URL 查询参数中暴露长期 Bearer Token）
  - Response: `Result<Map<String, Object>>` (`{"ticket": "st-...", "expiresInSeconds": 60}`)

### 2. 项目与代码工作区 (Project & Workspace)
- `POST /api/projects`：创建研发项目（自动绑定受控工作区）
  - Request: `{"name": "E-Commerce-Core", "description": "核心电商交易中台", "relativeWorkspacePath": "ecommerce-core"}`
  - Response: `Result<ProjectView>` (`{"id": "proj-...", "ownerId": "...", "name": "...", "status": "ACTIVE", "workspaceId": "ws-..."}`)
- `GET /api/projects`：获取当前用户的所有项目列表
  - Response: `Result<List<ProjectView>>`
- `GET /api/projects/{id}`：获取指定项目详情（触发水平越权检查）
  - Response: `Result<ProjectView>`
- `GET /api/projects/{id}/workspaces`：获取项目绑定的受控工作区列表
  - Response: `Result<List<WorkspaceView>>`
- `GET /api/projects/{id}/workspace`：获取项目主工作区详情
  - Response: `Result<WorkspaceView>`

#### 2.1 7 步受控项目接入向导 (Project Onboarding Wizard)
- `POST /api/projects/onboarding/start`：启动受控项目接入向导（Step 1：验证并连接代码源）
  - Request: `{"projectName": "...", "description": "...", "sourceConfig": {"sourceType": "LOCAL_DIR"|"REMOTE_GIT", "localPath": "...", "remoteUrl": "...", "secretRef": "...", "defaultBranch": "main"}}`
  - Response: `Result<OnboardingSessionView>`
- `POST /api/projects/onboarding/{sessionId}/step`：执行向导指定步骤（Step 2~7）
  - Request: `{"step": "CONNECT_SOURCE"|"VERIFY_PERMISSIONS"|"SELECT_DEFAULT_BRANCH"|"CREATE_WORKSPACE"|"SCAN_ENVIRONMENT"|"GENERATE_PROJECT_RULES"|"CREATE_INITIAL_BASELINE", "selectedBranch": "...", "customRules": "..."}`
  - Response: `Result<OnboardingSessionView>`
- `POST /api/projects/onboarding/{sessionId}/retry`：重试失败的向导步骤（`?step=...` 可选）
  - Response: `Result<OnboardingSessionView>`
- `GET /api/projects/onboarding/{sessionId}`：查询向导会话状态与各步骤审计执行记录
  - Response: `Result<OnboardingSessionView>`
- `POST /api/projects/onboarding/{sessionId}/abort`：中止向导并级联清理受控临时现场与文件目录
  - Response: `Result<OnboardingSessionView>`
- `POST /api/projects/onboarding/{sessionId}/complete`：完成向导接入，固化项目与初始基线并返回已就绪的 Project 视图
  - Response: `Result<ProjectView>`

#### 2.2 版本化上下文源 API (Versioned Context Sources)
- `GET /api/projects/{projectId}/context-sources`：查询项目绑定的所有版本化上下文源清单（AOCI 认知、项目规约、构建配置、测试规约）
  - Response: `Result<List<ContextSourceView>>`
- `POST /api/projects/{projectId}/context-sources`：注册新的版本化上下文源（自动计算 SHA-256 内容指纹并绑定 Git Commit）
  - Request: `{"sourceType": "TEST_CONVENTIONS", "name": "...", "relativePath": "...", "content": "...", "commitHash": "...", "securityLevel": "INTERNAL"}`
  - Response: `Result<ContextSourceView>`
- `GET /api/projects/{projectId}/context-sources/{sourceId}`：获取指定上下文源内容与元数据
  - Response: `Result<ContextSourceView>`

### 3. 受控工作区统一文件、租约与生命周期 API (Controlled Workspace API)
- `GET /api/workspaces/{workspaceId}/tree`：递归获取受控工作区文件树（支持相对子目录过滤 `?path=...`，严格忽略 `.git` 内部元数据）；
- `GET /api/workspaces/{workspaceId}/file?path={relPath}`：读取文件详情（返回 `WorkspaceFileDetailView`，严格限制单文件 2MB 预览上限，提供二进制自动识别与截断标识）；
- `POST /api/workspaces/{workspaceId}/file`：写入或更新工作区文件（Body: `{"path":"...","content":"..."}`，严格限制单文件 10MB 写入上限，执行 WorkspaceQuotaGuard 容量与文件数配额校验）；
- `POST /api/workspaces/{workspaceId}/file/rename`：安全重命名文件或目录（Body: `{"oldPath":"...","newPath":"..."}`）；
- `DELETE /api/workspaces/{workspaceId}/file?path={relPath}`：受控删除文件或目录；
- `GET /api/workspaces/{workspaceId}/download?path={relPath}`：受控下载单文件二进制流或文本内容；
- `GET /api/workspaces/{workspaceId}/archive`：将工作区受控文件归档打包为 ZIP 压缩包下载（自动排除 `.git` 元数据）；
- `POST /api/workspaces/{workspaceId}/git/snapshot`：将当前工作区改动提交为 JGit 快照 Commit 并生成待验收 `ArtifactEntity`；
- `GET /api/workspaces/{workspaceId}/diff`：获取结构化 Diff 对象（`Result<StructuredDiff>`）；
- `POST /api/workspaces/{workspaceId}/resync`：重新同步工作区至当前基线 Commit，清理脏现场；
- `POST /api/workspaces/{workspaceId}/change-baseline`：推进或重置受控工作区基线 Commit（Body: `{"targetCommitHash":"...","confirmed":true}`）；
- `POST /api/workspaces/{workspaceId}/discard`：放弃工作区所有未提交的本地修改并回滚至基线（Body: `{"confirmed":true,"reason":"..."}`）；
- `POST /api/workspaces/{workspaceId}/restore-snapshot`：恢复至指定的历史快照 Commit 或 Tag（Body: `{"snapshotCommitOrTag":"...","confirmed":true}`）；
- `GET /api/workspaces/{workspaceId}/quota`：查询工作区配额使用度（已用容量/上限、文件数/上限、执行时间、网络访问策略、当前并发执行数）；
- `POST /api/workspaces/{workspaceId}/lock/acquire`：申请工作区 Keyed 租约锁；
- `POST /api/workspaces/{workspaceId}/lock/renew`：为已持有的工作区租约锁续期；
- `POST /api/workspaces/{workspaceId}/lock/release`：主动释放工作区锁；
- `GET /api/workspaces/{workspaceId}/lock/status`：查询工作区当前锁定状态与剩余租期。

### 4. 智能体注册表与实例 (Agent Registry & Instances)
- `GET /api/agents`：查询平台已注册的 Agent 能力定义清单与可用状态；
- `GET /api/agents/instances`：查询当前可调度的智能体实例列表与运行时配置。

### 5. 即时通讯协同总线 (IM & Collaboration)
- `GET /api/im/conversations`：获取协同会话列表（返回 `ConversationView`，含参会成员 `participantIds`）；
- `POST /api/im/conversations`：创建会话（入参 `CreateConversationCommand`：title, type, participantIds）；
- `GET /api/im/conversations/{id}/messages`：查询历史消息列表（返回 `MessageView`，具备单调递增 `sequenceNum` 与 `schemaVersion: "v1"`）；
- `POST /api/im/conversations/{id}/messages`：发送消息与触发智能体路由（入参 `SendMessageCommand`）；
- `GET /api/im/conversations/{id}/stream`：**SSE 实时事件流通道**。

### 6. 工作流定义、模板、Orchestrator 计划、执行引擎与脱敏证据包 (Workflow Execution & Evidence Bundle)
- `GET /api/workflows/templates`：获取版本化工作流模板列表（`BUG_FIX_WITH_TESTS`, `NEW_FEATURE_ENDPOINT`，内置 7 阶段 Kahn DAG 与 4 大 Agent 角色）；
- `GET /api/workflows/templates/{id}`：获取模板详情与 Kahn 阶段拓扑规范；
- `POST /api/workflows/templates/{id}/instantiate`：根据模板动态实例化生成版本化 `WorkflowDefinition`；
- `POST /api/workflows/definitions/validate`：校验 DAG 工作流 DSL 拓扑合法性（Kahn 环路检测、自环拦截、孤岛节点拦截，计算阶段级并行执行批次 `executionStages` 与审批节点校验）；
- `POST /api/workflows/definitions`：创建并持久化版本化工作流定义；
- `GET /api/workflows/definitions`：查询工作流定义列表（支持 `?projectId=...`）；
- `POST /api/workflows/orchestrator/plans`：Orchestrator 生成结构化执行计划（入参 `GeneratePlanCommand`：projectId, workspaceId, templateType, taskGoal；包含预测受影响文件、子任务拆解、Token/成本/超时预算上限、质量门禁与工具权限清单）；
- `GET /api/workflows/orchestrator/plans/{planId}`：获取 Orchestrator 计划详情；
- `POST /api/workflows/orchestrator/plans/{planId}/confirm`：用户审查并确认计划（入参 `ConfirmPlanCommand`：confirmed, allowedHighImpactTools, decisionNote；确认后解锁高影响工具执行权限）；
- `POST /api/workflows/orchestrator/plans/{planId}/reject`：用户驳回计划（入参 `RejectPlanRequest`：reason）；
- `GET /api/workflows/projects/{projectId}/plans`：按项目查询历史规划列表；
- `POST /api/executions/runs`（或 `POST /api/runs`）：启动工作流运行实例（支持幂等键 `idempotencyKey` 防重，自动建立 JGit 基线 commit 与 Baseline Tag）
  - Request: `{"projectId": "<project-id>", "definitionId": "<definition-id>", "idempotencyKey": "idemp-uuid"}`
  - Response: `Result<WorkflowRunView>`
- `GET /api/executions/runs/{runId}`（或 `GET /api/runs/{runId}`）：查询指定工作流运行实例详情与执行状态
  - Response: `Result<WorkflowRunView>`
- `GET /api/executions/projects/{projectId}/runs`：获取项目下所有运行实例列表
  - Response: `Result<List<WorkflowRunView>>`
- `GET /api/executions/runs/{runId}/steps`：获取运行实例的各步骤节点（StepRun）状态与输出摘要
  - Response: `Result<List<StepRunView>>`
- `GET /api/executions/runs/{runId}/events`：支持基于 sequence 游标断点续传的事件回放流（`?afterSeq=<number>`）
  - Response: `Result<List<RunEventView>>`
- `POST /api/executions/runs/{runId}/events`：向运行实例追加持久化审计事件
  - Request: `{"eventType": "STEP_COMPLETED", "payload": "..."}`
  - Response: `Result<RunEventView>`
- `GET /api/executions/runs/{runId}/stream`（或 `GET /api/runs/{runId}/stream`）：**Run 专属 SSE 实时事件流通道**
  - 请求头：支持 `Last-Event-ID: <seq>`；亦支持查询参数 `?lastEventId=<seq>&ticket=<streamTicket>`；
  - 特性：自动按单调递增 `sequenceNum` 补发断点遗漏事件，并持续流式推送运行过程中的实时事件。
- `GET /api/executions/runs/{runId}/evidence`（或 `GET /api/runs/{runId}/evidence`）：**导出脱敏运行证据包 (`Result<RunEvidenceBundleView>`)**
  - 包含脱敏 DSL 快照、Run/Step 状态、单调事件序列、Provider/Runtime 审计、`simulated` 标识、JGit 结构化 Diff、审批记录、部署健康轨迹与确定性 SHA-256 `checksum`。
- `GET /api/executions/runs/{runId}/evidence/download`（或 `GET /api/runs/{runId}/evidence/download`）：下载独立 JSON 附件格式脱敏运行证据包（附带 `X-Evidence-Checksum` 与 `X-Execution-Mode` 响应头）。
- `POST /api/executions/runs/{runId}/cancel`（或 `POST /api/runs/{runId}/cancel`）：取消运行实例
  - Request: `{"reason": "用户手动终止"}`
  - 级联触发 CancelToken，强平子进程与中断执行线程，将 Run 及未完成 Step 状态收敛为 `CANCELLED`。
- `POST /api/executions/runs/{runId}/pause`：暂停运行实例
- `POST /api/executions/runs/{runId}/resume`：恢复运行实例
- `POST /api/executions/steps/{stepRunId}/retry`（或 `POST /api/executions/runs/{runId}/steps/{stepRunId}/retry`）：重试失败的 Step 节点
- `GET /api/executions/runs/{runId}/operations`：**查询运行运维全景视图 (`Result<RunOperationsView>`)**
  - 包含单一统一状态图（`unifiedStatus`、`nextAction`、`requiresHumanIntervention`）、待办决策清单（`pendingDecisions`）、受控工具副作用收据（`toolSideEffects`）、三层拓扑分层上下文记忆摘要（`memorySummary`）、产物血缘节点（`artifactLineage`）、最近事件序列（`recentEvents`）、分布式租约健康度（`leaseStatus`）与三级重试归因（`failureCategory`、`failureReason`、`totalRetries`）。
- `POST /api/executions/runs/{runId}/steps/{stepRunId}/rework`：**步骤打回重做 (`Result<StepRunView>`)**
  - Request: `{"feedback": "单元测试覆盖率不足，请补充边界条件测试"}`
  - 将指定 StepRun 状态重置为 `PENDING`，并将评审人意见注入步骤上下文；工作流重新调度执行该步骤。
- `POST /api/executions/runs/{runId}/reject`：**全盘驳回运行 (`Result<WorkflowRunView>`)**
  - Request: `{"reason": "方案不符合架构设计规范"}`
  - 将受控工作区安全重置至 Baseline Commit，终止所有未完成 Step，将 WorkflowRun 状态收敛为 `REJECTED` 并记录审计事件。
- `GET /api/executions/runs/{runId}/evidence/export`：**导出带 SHA-256 校验和的运行证据包 (`Result<RunEvidenceBundleView>`)**
  - 导出包含脱敏完整凭证包、质量门禁指纹与交付元数据的全量运行审计。
- `GET /api/approvals/runs/{runId}`：查询指定 Run 关联的人工审批门禁列表
- `POST /api/approvals/{approvalId}/approve` & `POST /api/approvals/{approvalId}/reject`：审批通过或驳回阻塞节点

### 7. 产物审计、审查与安全回滚 (Artifacts & Safe Rollback)
- `GET /api/audit/artifacts/run/{runId}`：获取指定 Run 的所有交付物与快照记录；
- `GET /api/audit/artifacts/{artifactId}`：获取产物详情、快照 commit 哈希与文件 SHA-256 清单；
- `GET /api/audit/artifacts/lineage/{runId}`：**获取产物全生命周期血缘图谱 (`Result<ArtifactLineageView>`)**
  - 穿透聚合从 `Run -> StepRun -> Artifact -> JGit Commit Hash -> File Checksums -> Approvals` 全生命周期拓扑血缘；
- `GET /api/audit/artifacts/{artifactId}/diff`：**获取产物 JGit 结构化行级 Diff 审查数据 (`Result<StructuredDiff>`)**
  - 返回快照 commit 相对基线的受影响文件列表、新增/删除行数、`diffText` 行级补丁、二进制与冲突标记；
- `GET /api/audit/artifacts/{artifactId}/quality-gate`：**获取产物质量门禁评估报告 (`Result<QualityGateReport>`)**
  - 评估 5 大核心维度（`BUILD_VERIFICATION`, `UNIT_TEST_REGRESSION`, `STATIC_ANALYSIS_GUARD`, `SECRET_LEAK_SCAN`, `DIFF_BUDGET_GUARD`），返回加权总分（0-100）、门禁状态（`PASSED`, `WARNING`, `FAILED`）、规则明细与 SHA-256 指纹校验和。
- `POST /api/audit/artifacts/{artifactId}/quality-gate/evaluate`：**实时重新评估产物质量门禁 (`Result<QualityGateReport>`)**
- `GET /api/audit/artifacts/{artifactId}/pr-draft`：**生成标准化 Pull Request 草稿规范 (`Result<PullRequestDraftView>`)**
  - 输出可直接复制至 GitHub / GitLab 的结构化 PR 草稿，包含规范 Markdown 正文、变动统计、门禁评分徽章与测试建议。
- `POST /api/audit/artifacts/{artifactId}/revert-file`：**单文件非破坏性精准回滚 (`Result<ArtifactView>`)**
  - Request: `{"filePath": "src/main/java/Foo.java"}`（亦支持 `?filePath=...` 查询参数）；
  - 基于 JGit 将指定单个修改/新增文件精准检出还原至 baseline commit，其他工作区已变更文件与未提交内容完全保持完好，发布 `FILE_REVERTED` 审计事件并更新产物快照。
- `POST /api/audit/artifacts/{artifactId}/accept`：人工审查通过该 Step 产物（更新审查状态为 `ACCEPTED` 并记录审计事件）；
- `POST /api/audit/artifacts/{artifactId}/reject`：人工审查拒绝产物（Body: `{"reason":"..."}`，更新审查状态为 `REJECTED`）；
- `POST /api/audit/artifacts/{artifactId}/revert`：安全非破坏性全量回滚产物（严格还原目标 Step 所影响的文件至基线 commit，杜绝 `git reset --hard` 硬重置，生成可追踪 Revert Commit 并更新状态为 `REVERTED`）。

### 8. 历史非受控工作区接口退役说明 (`ADR-003`)
- 生产 profile 不注册早期接收客户端工作区路径参数的 `/api/workspace/*` 接口；开发兼容路径只用于迁移诊断，不得产生新的产品数据。所有正式工作区读写、Diff 与快照操作均使用基于服务端 `workspaceId` 解析的 `/api/workspaces/{workspaceId}/*`（见第 3 节）。

### 9. 模版预览与部署清单生成 (Sandbox & Deploy)
- `GET /api/sandbox/preview/{id}`：挂载并实时预览 Agent 协同生成的 Web 静态模版页面；
- `POST /api/sandbox/deploy/{id}`：自动化导出 Nginx Dockerfile 镜像构建配置与部署清单。

### 10. 系统探针 (Health Check)
- `GET /api/system/health`：获取系统状态、Java 运行时版本、操作系统与应用版本。

### 11. 大模型提供商生态与动态路由管理 (LLM Providers & Dynamic Routing)
- `GET /api/providers`：获取已注册的模型 Provider 列表（包含健康状态、延迟与熔断状态，API Key 严格脱敏）；
- `GET /api/providers/{id}`：获取指定 Provider 详情；
- `POST /api/providers`：注册新的大模型 Provider；
- `PUT /api/providers/{id}`：更新 Provider 的优先级、权重、能力标签与费率配置；
- `DELETE /api/providers/{id}`：注销指定 Provider；
- `POST /api/providers/route`：传入请求参数进行动态路由推演，返回首选 Primary Provider 与备用 Backup 候选链条；
- `POST /api/providers/{id}/test`：对指定 Provider 发起测试连通性 Ping 请求，测量真实延迟与连通状态。

### 12. Token 消耗审计与成本计量 (Token & Cost Accounting)
- `GET /api/token-usages/runs/{runId}`：按 WorkflowRun 实例查询所有步骤调用的详细 Token 消耗与成本审计记录；
- `GET /api/token-usages/steps/{stepRunId}`：按 StepRun 节点查询单次调用审计明细；
- `GET /api/token-usages/summary`：获取平台全局累计 Token 消耗（Prompt、Completion、Total）与总美元成本计量汇总。

### 13. 多智能体团队与协同拓扑 (Multi-Agent Teams & Coordination Topologies)
- `POST /api/teams`：创建多智能体协同团队（支持 `HIERARCHICAL` 主从层级、`PEER_TO_PEER` 对等协商、`ROUND_ROBIN` 轮询轮转拓扑，指定 leaderAgentId 与 maxTurns 阈值）；
- `GET /api/teams`：获取项目或全局团队列表（支持 `?projectId=...` 过滤）；
- `GET /api/teams/{id}`：获取团队详情（包含所有 TeamMember 成员角色、职责范围与系统提示词覆盖）；
- `POST /api/teams/{id}/members`：向团队动态新增成员；
- `DELETE /api/teams/{id}/members/{memberId}`：从团队中移除成员；
- `POST /api/teams/{id}/coordinate`：依据当前团队拓扑策略（Hierarchical / P2P / Round-Robin）推演下一协作发言人决策（返回 `NextSpeakerDecision`: CONTINUE, HANDOFF, SUMMARIZE, TERMINATE）。

### 14. 对话消息总线与上下文治理 (Conversation Message Bus & Context Governance)
- `POST /api/im/conversations` 与 `POST /api/conversations`：创建协同会话（支持绑定 `teamId`、单聊/群聊 `type` 与参与者）；
- `GET /api/im/conversations` 与 `GET /api/conversations`：获取会话列表（按最近更新排序）；
- `GET /api/im/conversations/{id}` 与 `GET /api/conversations/{id}`：获取会话详情（包含滚动历史摘要与累计 Token 消耗）；
- `GET /api/im/conversations/{id}/messages` 与 `GET /api/conversations/{id}/messages`：查询会话消息历史（支持游标增量查询 `?sinceSeq=...` 与私信隔离过滤 `?viewerId=...`）；
- `POST /api/im/conversations/{id}/messages` 与 `POST /api/conversations/{id}/messages`：发送消息（支持 `messageType: BROADCAST / DIRECT / SYSTEM`、`protocolType: NORMAL / REQUEST_REPLY / HANDOFF / SUMMARIZE` 与严格单调递增 `sequence_num`，内置死循环检测拦截）；
- `GET /api/im/conversations/{id}/context` 与 `GET /api/conversations/{id}/context`：获取经过滑动窗口 (`windowSize`) 与 Token 预算控制 (`maxTokens`) 裁剪后的上下文窗口 `ContextWindowView`；
- `POST /api/im/conversations/{id}/summarize` 与 `POST /api/conversations/{id}/summarize`：手动或自动触发滚动历史摘要浓缩，将老消息压缩为紧凑前情要点；
- `POST /api/im/conversations/{id}/coordinate` 与 `POST /api/conversations/{id}/coordinate`：驱动团队拓扑执行下一协作轮次。

### 15. 工作区沙箱与部署自动化 (Sandbox & Deployment Automation)
- `POST /api/deployments`：创建并启动项目构建与部署
  - Request: `{"projectId": "<project-id>", "artifactId": "<artifact-id>", "target": "STATIC_PREVIEW", "sandboxType": "LOCAL_PROCESS", "templateType": "STATIC_WEB_TEMPLATE", "healthCheckPath": "/"}`
  - 准入门禁（Admission Control）：强制校验关联 Artifact 审查状态，若产物为 `GATE_FAILED` 或 `REJECTED` 则强行拦截并返回 `400 PARAM_ERROR`，杜绝未过门禁产物泄漏至沙箱环境；
  - Response: `Result<DeploymentResponse>` (`{"id": "dep-...", "status": "RUNNING", "port": 18000, "url": "http://127.0.0.1:18000/", ...}`)
- `GET /api/deployments/{id}`：查询指定部署详情与实时运行状态 (`CREATED`, `BUILD`, `START`, `HEALTH_CHECK`, `RUNNING`, `STOP`, `CLEANUP`, `STOPPED`, `FAILED`)
  - Response: `Result<DeploymentResponse>`
- `GET /api/deployments/{id}/logs`：查询部署构建与运行完整控制台日志
  - Response: `Result<String>`
- `POST /api/deployments/{id}/stop`：主动终止运行中部署并释放绑定的本地端口
  - Response: `Result<DeploymentResponse>`
- `POST /api/deployments/{id}/probe`：**主动执行沙箱 HTTP 健康探针 (`Result<DeploymentResponse>`)**
  - 向预览环境的健康检查路径（如 `/`）发起真实 HTTP GET 探测，记录响应 HTTP 状态码与往返延迟（ms），更新探针时间戳并在响应中反馈健康详情。
- `GET /api/deployments?projectId={projectId}`：按项目分页或全量查询历史部署列表
  - Response: `Result<List<DeploymentResponse>>`
- `GET /api/sandbox/preview/{projectId}`：渲染并返回项目实时前端静态预览 HTML 页面
  - Response: `text/html`
- `POST /api/sandbox/deploy/{projectId}`：一键部署并生成容器镜像交付清单与 Dockerfile
  - Response: `Result<DeploymentManifest>`

### 16. Agent 注册表、工具体系、上下文装配与离线评测 (Agent Registry, Tools, Context & Evaluation)
- `GET /api/agents`：查询系统注册的 Agent 定义列表（含 4 大内置规范角色：`backend-architect`, `backend-developer`, `qa-tester`, `security-reviewer`）；
- `GET /api/agents/{defKey}`：查询指定 Agent 定义详情（含工具白名单、Provider 策略、Token 预算、安全级别与停止条件）；
- `GET /api/agents/tools`：查询系统 7 大核心内置工具定义清单（含参数 JSON Schema、高影响标识与输出配额上限）；
- `POST /api/agents/context-assembly`：6 层分层上下文组装引擎（按层级 Token 预算分配、截断策略、来源追溯与安全级别标记，防爆防泄漏）；
- `POST /api/agents/evaluations/run`：触发运行离线自动化评测套件（入参可选指定模型 `model`；覆盖 5 大任务类型：新端点、缺陷修复、代码重构、测试补齐、对抗注入防御）；
- `GET /api/agents/evaluations/latest`：获取最新离线评测报告（包含综合通过率 `passRate`、工具误用率 `toolMisuseRate`、Diff 采纳率与单任务明细）；
- `GET /api/agents/platforms`：查询受支持的 Agent 执行平台与沙箱形态；
- `POST /api/agents/execute`：单次受控 Agent 任务直接执行。

---

## 三、 SSE 事件类型定义

### 1. IM 协同会话流 (`/api/im/conversations/{id}/stream` 或 `/api/conversations/{id}/stream` / `/events`)
支持 `Last-Event-ID` 请求头或 `?sinceSeq=...` 参数进行断点续传历史补发：
| 事件名称 | 数据载荷格式 | 场景说明 |
| :--- | :--- | :--- |
| `connected` | `{"conversationId": "...", "status": "ready"}` | 客户端握手建立连接时发送 |
| `message` | `MessageView` 完整 JSON 结构（包含 `sequenceNum`, `messageType`, `protocolType`） | 收到新消息、Orchestrator 交互卡片或系统通知 |
| `message_delta` | `{"sender": "BackendArchitect", "delta": "..."}` | 智能体实时打字机流式 Token 增量 |
| `handoff` | `{"fromAgent":"...","toAgent":"...","reason":"..."}` | 智能体之间触发任务委托交接 |
| `loop_detected` | `{"conversationId":"...","loopType":"...","reason":"..."}` | 检测到死循环或短周期振荡碰撞，触发自动熔断保护 |
| `summary_generated`| `{"conversationId":"...","summary":"..."}` | 滚动历史摘要生成并更新 |
| `heartbeat` | `{"timestamp": 179128...}` | 保持长连接活性的周期心跳包 |

### 2. 工作流执行流 (`/api/executions/runs/{runId}/stream`)
每个事件严格附带单调递增 `id: <sequenceNum>`，支持 `Last-Event-ID` 断点续传重放：
| 事件名称 | 数据载荷格式 | 场景说明 |
| :--- | :--- | :--- |
| `connected` | `{"runId": "...", "replayedCount": N, "status": "STREAM_OPENED"}` | 连接就绪握手 |
| `RUN_STARTED` | `Workflow execution started` | 运行实例启动 |
| `RUN_STATE_CHANGED` | `{"fromStatus":"...","toStatus":"...","actor":"...","reason":"..."}` | 运行状态机严格流转审计 |
| `STEP_STATE_CHANGED` | `{"stepRunId":"...","fromStatus":"...","toStatus":"...","attempt":1}` | 步骤状态机严格流转审计 |
| `STEP_STARTED` | `{"stepRunId":"...","nodeId":"...","attempt":1}` | 步骤执行开启 |
| `TOKEN` | `{"token":"..."}` | 实时大模型 Token 输出流 |
| `MESSAGE` | `{"sender":"...","content":"..."}` | 智能体运行过程消息 |
| `TOOL_CALL` | `{"tool":"...","input":{...}}` | 工具调用行为 |
| `FILE_CHANGE` | `{"path":"...","changeType":"CREATED"}` | 工作区文件受控变动 |
| `LOG` | `{"level":"INFO","message":"..."}` | 结构化执行日志 |
| `USAGE` | `{"promptTokens":120,"completionTokens":250,"cost":0.0004}` | Token 消耗与成本核算 |
| `PROVIDER_FALLBACK`| `{"failedProvider":"...","backupProvider":"...","statusCode":429}` | 主 Provider 故障触发高可用自动容灾降级 |
| `STEP_COMPLETED` | `{"stepRunId":"...","nodeId":"..."}` | 步骤成功完成 |
| `STEP_RETRYING` | `{"stepRunId":"...","nextAttempt":2}` | 步骤失败触发重试治理 |
| `RUN_CANCELLED` | `{"reason":"..."}` | 用户手动取消或协同终止 |
| `RUN_TIMED_OUT` | `{"timeoutSeconds":60}` | 看门狗强平超时 |
| `RUN_COMPLETED` | `{"status":"SUCCEEDED"}` | 工作流实例完成 |

---

## 四、 REST/SSE 接口与前端调用全景矩阵 (Phase 0 基线盘点)

以下矩阵覆盖当前后端全部 18 个 `@RestController` 与前端 `agenthub-frontend/src/services/api.ts` 调用映射：

| 接口路径族 | Owner 模块 | 来源 Controller | 前端调用入口 (`services/api.ts`) | 认证与资源归属校验 | 幂等与并发语义 | 状态 | 核心验证测试 | 删除 / 收敛日期 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `/api/auth/*` (`register`, `login`, `refresh`, `logout`, `me`, `stream-ticket`) | `identity` | `AuthController` | `login`, `register`, `logout`, `createStreamTicket` | 登录/注册公开但受 `AuthRateLimiter` 限流；其余强制 Bearer/Cookie；`stream-ticket` 60s 单次消费 | 注册邮箱 `UNIQUE(email)`；`logout` 幂等写入 `invalidated_tokens` | `ACTIVE` | `PhaseASecurityAndResourceBoundaryIntegrationTest` | 长期保留（主链路） |
| `/api/projects`, `/api/projects/{id}`, `/api/projects/{id}/workspace(s)` | `project` | `ProjectController` | `listProjects`, `createProject`, `getProject`, `getWorkspace` | 强制登录 + `ResourceAccessGuard` 校验 `Project.ownerId` | 查询幂等；创建绑定独立 `workspaceId` | `ACTIVE` | `PhaseASecurityAndResourceBoundaryIntegrationTest` | 长期保留（主链路） |
| `/api/workspaces/{workspaceId}/*` (`tree`, `file`, `files`, `file/rename`, `download`, `archive`, `diff`, `git/snapshot`, `lock/*`) | `project` | `WorkspaceController` | `listFiles`, `getFileContent`, `saveFile`, `getStructuredDiff`, `getDiff`, `getLockStatus` | 强制登录 + 服务端解析 `workspaceId` 并校验所属 `Project.ownerId` | `workspace_locks` 毫秒级租约互斥 + JGit commit 树快照 | `ACTIVE` | `WorkspaceSecurityAndJGitAuditIntegrationTest` | 长期保留（唯一正式工作区接口） |
| `/api/workflows/definitions`, `/api/workflows/definitions/validate` | `orchestration` | `WorkflowDefinitionController` | `listDefinitions`, `validateDsl` | 强制登录；关联项目时校验 `Project.ownerId` | `validate` 纯函数无副作用；定义按 `id + version` 存储 | `ACTIVE` | `WorkflowDefinitionAndApprovalControllerIntegrationTest` | 长期保留；Phase 3 升级为 `WorkflowTemplate` |
| `/api/executions/runs*`, `/api/executions/projects/{projectId}/runs`, `/api/executions/steps/{stepRunId}/retry`, `/api/executions/runs/{runId}/operations`, `/rework`, `/reject`, `/evidence/export` | `execution` | `ExecutionController` | `startRun`, `getRun`, `listRunsByProject`, `listStepRuns`, `listEvents`, `cancelRun`, `getRunEvidence`, `exportRunEvidenceFile`, `getRunOperations`, `reworkStep`, `rejectRun`, `exportRunEvidence`, `LiveExecutionTerminal` (SSE) | 强制登录 + `Project.ownerId` 归属校验；SSE 支持一次性 `ticket` 或 Cookie | `startRun` 支持 `idempotencyKey` 防重；SSE 支持 `Last-Event-ID` 断点续传；`evidence` 含确定性 SHA-256 `checksum` | `ACTIVE` | `PersistentExecutionRecoveryAndLeaseTest`, `PhaseFProductQualityAndEvidenceBundleIntegrationTest`, `Phase4RunOperationsAndArtifactLineageTest`, `Phase5QualityGateAndDeliveryTest` | 长期保留（权威执行入口） |
| `/api/runs*` (`POST /api/runs`, `GET /api/runs/{runId}`, `/cancel`, `/stream`, `/evidence`, `/evidence/download`) | `execution` | `RunAliasController` | `LiveExecutionTerminal` (兼容别名) | 同 `ExecutionController` | 委托 `ExecutionApplication`，与 `/api/executions/runs` 共享同一状态机与幂等键 | `COMPAT_ALIAS` | `ExecutionSseStreamAndReconnectionTest`, `PhaseFProductQualityAndEvidenceBundleIntegrationTest` | Phase 1 评估与 `/api/executions/runs` 合并为单一路径 |
| `/api/approvals/runs/{runId}`, `/api/approvals/{id}/approve`, `/api/approvals/{id}/reject` | `orchestration` | `ApprovalController` | `listApprovals`, `approve`, `reject` | 强制登录 + 关联 `Run -> Project.ownerId` 校验 | 状态机限制仅 `PENDING` 可转换至 `APPROVED`/`REJECTED` | `ACTIVE` | `HumanInTheLoopApprovalIntegrationTest` | 长期保留（主链路） |
| `/api/audit/artifacts/run/{runId}`, `/api/audit/artifacts/{id}` (`/accept`, `/reject`, `/revert`, `/diff`), `/api/audit/artifacts/lineage/{runId}`, `/quality-gate*`, `/pr-draft`, `/revert-file` | `audit` | `ArtifactController` | `revertArtifact`, `getArtifactLineage`, `getArtifactDiff`, `acceptArtifact`, `rejectArtifact`, `getQualityGateReport`, `evaluateQualityGate`, `getPullRequestDraft`, `revertSingleFile` | 强制登录 + 关联 `Run -> Project.ownerId` 校验 | `accept`/`reject`/`revert` 状态校验，单文件/全量非破坏性精准回滚，禁止重复 `revert`（错误码 `3011`） | `ACTIVE` | `WorkspaceSecurityAndJGitAuditIntegrationTest`, `Phase4RunOperationsAndArtifactLineageTest`, `Phase5QualityGateAndDeliveryTest` | 长期保留（主链路） |
| `/api/deployments*` (`POST`, `GET /{id}`, `GET /{id}/logs`, `POST /{id}/stop`, `POST /{id}/probe`, `GET ?projectId=`) | `sandbox` | `DeploymentController` | `listDeployments`, `createDeployment`, `stopDeployment`, `getDeploymentLogs`, `probeDeployment` | 强制登录 + `Project.ownerId` 校验 + 强制绑定已通过质量门禁 (`!GATE_FAILED`) 与审查的 `Artifact` | 端口池原子分配；`stop` 幂等收敛至 `STOPPED`；探针执行真实 HTTP 延迟探测 | `ACTIVE` | `PhaseDSandboxLifecycleAndDeliveryIntegrationTest`, `Phase5QualityGateAndDeliveryTest` | 长期保留（主链路） |
| `/api/providers*` (`GET`, `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /route`, `POST /{id}/test`) | `agent` | `ProviderController` | `listProviders`, `evaluateRoute` | 强制登录；修改/删除校验 `Provider.ownerId` | `route` 无副作用推演；`PUT`/`DELETE` 幂等 | `ACTIVE` | `ProviderAndTokenUsageControllerIntegrationTest` | 长期保留（主链路） |
| `/api/token-usages/runs/{runId}`, `/steps/{stepRunId}`, `/summary` | `agent` | `TokenUsageController` | `workflowsApi.getTokenSummary` | 强制登录 + 关联 `Run -> Project.ownerId` 校验 | 只读聚合查询，完全幂等 | `ACTIVE` | `ProviderAndTokenUsageControllerIntegrationTest` | 长期保留（主链路） |
| `/api/agents`, `/api/agents/platforms`, `/api/agents/execute` | `agent` | `AgentRegistryController` | 后端内部/管理查询 | `AuthFilter` 统一鉴权 | `GET` 幂等 | `ACTIVE` | `AgentAdapterTest`, `Phase1ModuleConvergenceAndContractTest` | Phase 1 已迁入 `com.agenthub.agent.api` |
| `/api/teams*` (`POST`, `GET`, `GET /{id}`, `/members`, `/coordinate`) | `team` | `TeamController` | `workflowsApi.getTeam` | 强制登录 + 关联 `Project.ownerId` 校验 | 查询幂等；`coordinate` 基于当前轮次推演 | `ISOLATED_EXT` | `TeamAndConversationControllerIntegrationTest` | 作为协作扩展隔离维护 |
| `/api/conversations*` (`POST`, `GET`, `/{id}/messages`, `/{id}/stream`, `/{id}/context`, `/{id}/summarize`, `/{id}/coordinate`) | `conversation` | `ConversationController` | `workflowsApi.listConversations`, `getMessages`, `sendMessage` | 强制登录 + `Conversation.ownerId` 与 `MessageVisibilityFilter` | 消息通过 128 分段锁 + 悲观写锁保证 `sequence_num` 单调递增 | `ISOLATED_EXT` | `TeamAndConversationControllerIntegrationTest`, `ConversationSseStreamAndReconnectionTest` | 唯一正式会话接口（前端已全量切换至 `/api/conversations`） |
| `/api/im/conversations*` | `adapter.web` | `IMController` | **0 前端调用**（已迁至 `/api/conversations`） | `@Deprecated` + `@Profile("!prod")`；`prod` 返回 `410 Gone` | `LegacyEndpointMigrationFilter` 统计调用计数 | `DEPRECATED_DISABLED_IN_PROD` | `Phase1ModuleConvergenceAndContractTest` | 生产环境已卸载并拦截 `410 Gone` |
| `/api/sandbox/preview/{projectId}`, `/api/sandbox/deploy/{projectId}` | `adapter.web` | `SandboxController` | **0 前端调用**（已迁至 `/api/deployments`） | `@Deprecated` + `@Profile("!prod")`；`prod` 返回 `410 Gone` | `LegacyEndpointMigrationFilter` 统计调用计数 | `DEPRECATED_DISABLED_IN_PROD` | `Phase1ModuleConvergenceAndContractTest` | 生产环境已卸载并拦截 `410 Gone` |
| `/api/system/health`, `/api/system/contracts/v1`, `/api/v1/openapi.json`, `/api/system/legacy-endpoints` | `ops` | `SystemHealthController` | `authApi.getHealth` | 公开探针与契约规范（不暴露内部敏感配置） | 只读完全幂等，附带 `X-API-Version: v1` 与 `UTC` 时区头 | `ACTIVE` | `SystemHealthTest`, `Phase1ModuleConvergenceAndContractTest` | Phase 1 已迁入 `com.agenthub.ops.api` |
| `/api/workspace/*` (`diff`, `workflow/execute`, `files`, `file/content`, `file/save`) | `adapter.web` | `WorkflowAndDiffController` | **0 前端调用** | `@Deprecated` + `@Profile("!prod")`；`prod` 强制阻断并返回 `410 Gone` | `LegacyEndpointMigrationFilter` 统计调用计数 | `DEPRECATED_DISABLED_IN_PROD` | `WorkspacePathGuardTest`, `Phase0BaselineAndDebtFreezeGuardTest`, `Phase1ModuleConvergenceAndContractTest` | 生产环境已卸载并拦截 `410 Gone` |
