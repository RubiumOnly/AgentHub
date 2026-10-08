# AgentHub 核心架构设计说明书

本文档系统阐述 AgentHub 平台的领域建模、模块化单体包结构、四层架构分层规范、SPI 插件机制、Flyway 持久化基线与架构防腐守护。

> 核心架构决策请参阅 [ADR-001 ~ ADR-004](adr/ADR-001-modular-monolith-architecture.md)。  
> 长期产品化演进路线请参阅 [《AgentHub 长期产品化与简历项目实施计划》](AGENTHUB_LONG_TERM_PLAN.md)。  
> 当前系统经过代码验证的真实边界与局限请参阅 [《当前真实能力边界与架构现状白皮书》](CURRENT_CAPABILITY_BOUNDARIES.md)。

---

## 一、 领域驱动设计 (DDD) 模块化单体与分层架构

AgentHub 采用**模块化单体 (Modular Monolith)** 架构形态，严格划分 9 大业务领域模块与通用支撑模块，杜绝技术层大杂烩堆砌。

```mermaid
flowchart TD
    subgraph Client [前端与客户端]
        Web[Next.js 14 Bento Console]
        CLI[API / CLI Consumers]
    end

    subgraph SecurityFilter [安全与请求链路治理]
        CorrFilter[RequestCorrelationFilter (X-Request-ID/X-Correlation-ID)]
        AuthFilter[AuthFilter (Bearer Token / Dev Token 解析)]
        Guard[ResourceAccessGuard (跨租户资源归属越权防御)]
    end

    subgraph API [用户接口适配层 (Controllers)]
        AuthCtrl[AuthController]
        ProjCtrl[ProjectController]
        ExecCtrl[ExecutionController]
        IMCtrl[IMController]
        WfCtrl[WorkflowAndDiffController]
        AgentCtrl[AgentRegistryController]
        SandboxCtrl[SandboxController]
        SysCtrl[SystemHealthController]
    end

    subgraph Application [应用服务层 (Application Services & Ports)]
        AuthApp[AuthApplication]
        ProjApp[ProjectApplication / WorkspaceApplication]
        ConvApp[ConversationApplication]
        ExecApp[ExecutionApplication]
        AgentApp[AgentApplication]
        SandboxApp[SandboxApplication]
    end

    subgraph Domain [九大领域核心 (Domain Models & Ports)]
        Identity[identity: UserEntity, TokenProvider, PasswordEncoder]
        Project[project: ProjectEntity, WorkspaceEntity, PathGuard]
        Agent[agent: AgentDefinition, AgentInstance, Provider]
        Conv[conversation: Conversation, Participant, Message(v1)]
        Orch[orchestration: WorkflowDefinition DSL]
        Exec[execution: WorkflowRun, StepRun, RunEvent, StateMachine]
        Audit[audit: Artifact, JGit Baseline, Diff Engine]
        Sandbox[sandbox: Deployment, Preview]
        Shared[shared: Result, RequestContext, ErrorCode, BusinessException]
    end

    subgraph Persistence [持久化与基础设施层]
        Flyway[Flyway Migrations (V1 Schema / V2 Seed)]
        H2MySQL[H2 (MySQL Mode) / MySQL 8.0]
        GitFS[JGit Repository / Controlled Workspaces]
    end

    Client --> SecurityFilter
    SecurityFilter --> API
    API --> Application
    Application --> Domain
    Domain --> Persistence
```

### 1. 九大核心领域模块分布 (`com.agenthub.*`)

- **`identity`**：用户登录、注册、密码哈希（BCrypt）、Token 鉴权（HMAC-SHA256 与开发兼容模式）、跨租户资源归属校验（`ResourceAccessGuard`）；
- **`project`**：项目边界（`ProjectEntity`）、受控代码工作区（`WorkspaceEntity`）、受控路径解析与目录树安全提取；
- **`agent`**：智能体平台能力定义（`AgentDefinition`）、实例配置（`AgentInstance`）、模型提供商（`Provider`）；
- **`conversation`**：即时通讯会话（`ConversationEntity`）、参会关系表（`ConversationParticipantEntity`，彻底淘汰逗号分割字符串）、版本化与单调递增序号的消息流（`MessageEntity`，支持 `schema_version = "v1"` 与 `sequence_num`）；
- **`orchestration`**：任务编排引擎、版本化工作流 DSL 定义（`WorkflowDefinitionEntity`）；
- **`execution`**：执行实例状态机（`WorkflowRunEntity`，支持幂等键防重）、节点执行（`StepRunEntity`）、可审计与可重放运行时事件（`RunEventEntity`，支持按序号游标拉取）；
- **`audit`**：版本审计基线、JGit 原生 Diff 增删行统计器、交付物实体（`ArtifactEntity`）；
- **`sandbox`**：Web 预览沙箱、Dockerfile 构建清单生成、部署记录（`DeploymentEntity`）；
- **`shared`**：全局统一响应结构 `Result<T>`（携带 `requestId`）、上下文链路传递 `RequestContext`（MDC 追踪）、全域业务异常错误码规范 `ErrorCode`。

### 2. 接口分层与防腐规范 (Controller - Application 隔离)

所有 Web Controller 必须严格遵循以下契约原则：
1. **禁止直接依赖持久化仓储 (Repository)**：Controller 仅能依赖对应领域的 `*Application` 接口；
2. **严禁将 JPA Entity 直接暴露给网络层**：请求参数统一通过 `*Command` / `*Query` 传递，响应体必须映射为 `*View` DTO；
3. **架构守卫自动测试**：通过 ArchUnit 架构测试套件 (`ArchUnitArchitectureTest`) 在 CI/CD 阶段强制执行依赖方向验证。

---

## 二、 数据库版本管理与持久化基线 (Flyway)

系统全面集成 **Flyway** 作为唯一的数据库 Schema 演化机制，杜绝生产环境使用 `hibernate.ddl-auto: update` 带来的脏结构风险。

- **`V1__init_schema.sql`**：统一创建 18 张核心业务表，涵盖 `users`, `projects`, `workspaces`, `agent_definitions`, `agent_instances`, `providers`, `teams`, `team_members`, `conversations`, `conversation_participants`, `messages`, `workflow_definitions`, `workflow_runs`, `step_runs`, `run_events`, `artifacts`, `approvals`, `deployments`；
  - 核心索引：`idx_messages_conv_seq(conversation_id, sequence_num)`, `idx_run_events_run_seq(run_id, sequence_num)`, `idx_wf_runs_idemp(idempotency_key)`；
- **`V2__seed_system_baseline.sql`**：注入系统初始安全基线数据（系统用户、默认项目、默认工作区、四大核心智能体定义：Orchestrator、BackendArchitect、FrontendEngineer、QAAuditor）；
- **兼容性验证**：Schema DDL 经专门设计，100% 兼容 H2 (MySQL Mode) 本地快速回归测试与生产 MySQL 8.0 严苛验证；
- **事务与查询边界**：生产与测试配置全面启用 `spring.jpa.open-in-view: false`，杜绝因延迟加载穿透导致的隐藏 N+1 查询与事务悬挂问题。

---

## 三、 统一认证鉴权与安全防御体系

1. **链路追踪 (Request Tracing)**：`RequestCorrelationFilter` 自动拦截/分配 `X-Request-ID` 与 `X-Correlation-ID`，并将追踪信息绑定至 MDC 日志与 HTTP 响应头；
2. **身份鉴权 (Authentication)**：基于无状态 Token 令牌机制。生产与测试环境通过 Authorization Header (`Bearer <token>`) 传递，开发环境支持透明安全退化；
3. **水平越权防御 (ResourceAccessGuard)**：执行跨用户资源拦截。任何用户试图操作或查询不属于自己的 Project / Workspace / Conversation 时，一律拦截并返回 HTTP 403 `FORBIDDEN`，杜绝多租户数据泄露。

---

## 四、 基于 ArchUnit 的架构防腐与质量保障

系统引入 ArchUnit 进行代码架构自动化守卫，测试用例 `ArchUnitArchitectureTest` 覆盖以下规则：
- 控制器不得越权直接注入或调用 JPA Repository 仓储；
- 控制器必须收敛于 `adapter.web` 或 `*.api` 包路径下；
- 应用服务必须收敛于 `*.application` 包路径下；
- 51 项单元、领域逻辑与边界防御测试（含 `FlywayMigrationAndSchemaTest`、`AuthenticationAndResourceGuardTest`、`OpenInViewTransactionBoundaryTest` 等）在构建阶段全量绿灯执行。

