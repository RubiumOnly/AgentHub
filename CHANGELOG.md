# 更新记录 (Changelog)

所有关键架构升级、特性新增与重要缺陷修复均按版本记录于此。

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
  - 实现基于 Token 认证体系（支持 HMAC-SHA256 与开发兼容模式）及 BCrypt 强哈希密码校验；
  - 新增 `ResourceAccessGuard`，实施跨用户/跨租户资源归属强制鉴权（403 `FORBIDDEN`）；
  - 全链路集成 `RequestCorrelationFilter`，自动透传 `X-Request-ID` 与 `X-Correlation-ID`，并在统一响应体 `Result<T>` 中返回 `requestId`；
  - 全局配置 `spring.jpa.open-in-view: false`，规范事务边界；
- **自动化架构守卫与 51 项防御性测试矩阵**：
  - 引入 ArchUnit 架构自动化守护 (`ArchUnitArchitectureTest`)，强制约束 Controller 与 Repository 的调用隔离与分层归属；
  - 新增 Flyway 迁移校验、鉴权与防越权测试、单调递增消息时序验证、执行引擎幂等持久化测试、事务边界隔离测试等 16 项高密度用例；
  - 后端 51/51 项单元、集成与防御性测试矩阵 100% 绿灯通过；前端 Next.js 14 生产构建通过。

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
