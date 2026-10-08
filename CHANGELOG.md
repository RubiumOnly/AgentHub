# 更新记录 (Changelog)

所有关键架构升级、特性新增与重要缺陷修复均按版本记录于此。

---

## [v1.2.0] - 2026-10-08

### 🌟 阶段 2：安全工作区与 JGit 审计升级 (Phase 2 Deliverables)
- **深化受控工作区管理与多维沙箱防御**：
  - `WorkspaceResolver` 升级为基于 `workspaceId` + `relativePath` 受控解析，自动绑定数据库工作区或受控根目录；
  - 完善 `Path.normalize()`、`toRealPath()`、符号链接越权检测与根目录 Containment 校验；
  - 严格拦截设备文件（Windows 保留字 `CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9` 及其变种拓展名）；
  - 严格拦截 `.git` 内部篡改、大小写变种（`.GIT`, `.Git`）与尾随点（`.git.`）；
- **统一受控工作区文件操作 API**：
  - 暴露标准 RESTful 控制器 `WorkspaceController`（`/api/workspaces/{workspaceId}/tree`, `/file`, `/file/rename`, `/file`, `/diff`, `/lock/*`）；
  - 建立单文件 10MB 写入上限防御与 2MB 预览上限限制；
  - 增加二进制文件检测引擎（扩展名 + 首 4KB 空字节探测），禁止直接返回乱码，提供安全截断与结构化视图；
- **JGit 审计与快照治理**：
  - 每次 Workflow Run 启动时自动建立 JGit 基线 commit（`Baseline commit for Run <runId>`）并打上基线 Tag；
  - 每个重要 Step 完成时自动创建快照 commit（`[StepSnapshot]`）与 Tag，精确统计变更文件清单并计算 SHA-256 校验和落库；
- **结构化 Diff 输出引擎**：
  - JGit 模块输出结构化 Diff 对象（`StructuredDiff` 与升级版 `FileDiffEntry`），涵盖文件变更类型（ADD/MODIFY/DELETE/RENAME/COPY）、增减行数、标准 Unified Diff patch、是否二进制、是否重命名、冲突标记（`hasConflict`）及审查状态；
- **工作区并发与租约治理 (Keyed Lock)**：
  - 升级 `WorkspaceLockManager` 支持多 Owner 身份识别与租约超时（Lease TTL）模型；
  - 实现基于 TTL 的租约续期机制与超时防死锁自动回收（Deadlock Auto-Recovery）；
  - 保持与单 JVM 线程重入的 100% 向后兼容；
- **产物审查与安全非破坏性回滚**：
  - 实现 `ArtifactApplication` 与 `ArtifactController`，支持对 Step 产物执行 `accept`、`reject`、`revert`；
  - 回滚必须严格限定在受控 snapshot 基线内，禁止硬重置工作区（杜绝 `git reset --hard` 清空用户未提交工作的风险），仅将目标 Step 影响的文件还原至基线并产生审计 Revert Commit；
- **Flyway 数据库演进**：
  - 落地 `V3__phase2_workspace_audit_artifacts.sql`，为 `artifacts` 表补充 `review_status`, `reviewed_by`, `reviewed_at`, `review_comment` 审查字段与分布式锁租约表 `workspace_locks`；
- **全绿灯测试矩阵**：
  - 新增 `WorkspaceSecurityAndJGitAuditIntegrationTest` 13 项边界防御测试用例；
  - 后端 73/73 项单元、架构守卫与集成测试 100% 绿灯通过；前端 Next.js 14 生产构建 100% 绿灯。

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
