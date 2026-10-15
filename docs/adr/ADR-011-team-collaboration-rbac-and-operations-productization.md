# ADR-011：Phase 6 团队协作、组织角色与长期运维体系产品化

- **状态**：已采纳（Accepted）
- **日期**：2026-10-15
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §7 Phase 6（团队协作、组织角色与长期运维体系）

## 1. 背景与问题陈述

在 Phase 5 完成质量门禁策略、多维文件审查、受控沙箱预览部署与离线合规证据包导出后，AgentHub 已经形成了完整的 AI 代码变更交付闭环。然而，为了支撑团队级及企业级场景的稳定落地，平台需要跨越从“单兵/简易团队工具”向“多角色协同、组织权限隔离、高可观测与灾备保障的企业级交付平台”演进，面临以下关键痛点：

1. **细粒度权限管控与角色职责分离（Separation of Duties）缺失**：
   原有系统仅有粗粒度角色或团队所有权划分，无法满足真实研发组织中的分权制衡需求。例如，需要区分项目创建者（OWNER）、项目管理员（ADMIN）、高级维护者（MAINTAINER）、日常研发（DEVELOPER）、只读观察员（VIEWER）与独立安全合规审计员（AUDITOR）。必须严密防范越权写工作区、越权执行工作流、越权审批高危动作以及越权操作沙箱部署。
2. **安全审计流水不可篡改与全链路归因能力不足**：
   关键认证登录、权限提升、敏感配置修改与破坏性操作需要具备不可伪造的审计轨迹，需支持按用户、事件类型及时间范围结构化查询与追溯，支持脱机安全审计。
3. **长期运行灾备与确定性还原保障机制缺失**：
   在单机宿主或私有化部署环境下，面临突发硬件故障、存储损坏或人为误删的高危风险。系统缺乏规范的标准化灾备快照生成、校验和验证机制；还原过程缺乏校验、幂等保护与状态自愈能力。
4. **运维可观测性与 SLO 健康度缺乏聚合透视**：
   运维人员需要能够实时洞察系统吞吐、执行失败率、API 延迟 P95/P99、JVM 堆内存/GC 状态、工作区磁盘水位以及活跃连接数，并依据业务目标自动判定 SLO 健康度状态（HEALTHY / DEGRADED / UNHEALTHY），提供开箱即用的运维全景。

---

## 2. 决策内容

### 2.1 细粒度 RBAC 权限矩阵与团队协作模型

1. **六级项目角色定义（`ProjectRbacRole`）**：
   - `OWNER`：项目所有者，拥有全部权限（项目删除/更名、成员管理、写工作区、执行工作流、审批、部署、查看与审计）；
   - `ADMIN`：项目管理员，拥有除转让/销毁所有权外的全部管理及执行权限；
   - `MAINTAINER`：核心维护者，拥有工作区读写、工作流执行、质量门禁决议与部署运维权限；
   - `DEVELOPER`：研发工程师，拥有工作区读写、工作流触发与测试执行权限，不可直接批准高危发布与部署；
   - `VIEWER`：普通观察员，对项目、工作区、Run、Diff、Artifact 具备严格只读权限；
   - `AUDITOR`：安全审计员，具备全局只读与安全审计流水、合规证据包的专门检索审查权限。
2. **权限项集合（`ProjectPermission`）**：
   定义清晰的操作动词：`PROJECT_READ`、`PROJECT_WRITE`、`PROJECT_ADMIN`、`MEMBER_MANAGE`、`WORKSPACE_READ`、`WORKSPACE_WRITE`、`RUN_EXECUTE`、`APPROVAL_DECIDE`、`DEPLOYMENT_OPERATE`、`AUDIT_VIEW`。
3. **基于既有表的无破坏性映射与动态赋权**：
   - 充分复用既有 `teams` 与 `team_members` 关系表，不增加任何冗余物理表；
   - 增强 `ResourceOwnershipVerifier`：提供 `verifyWorkspaceWriteAccess`、`verifyRunExecuteAccess`、`verifyApprovalAccess`、`verifyDeploymentAccess` 等防御方法，统一在控制器层拦截非法越权操作。
4. **团队成员协作端点**：
   - `GET /api/teams/projects/{projectId}/role`：获取当前登录用户在项目中的有效角色；
   - `GET /api/teams/projects/{projectId}/members`：列出项目协作成员列表；
   - `POST /api/teams/projects/{projectId}/members`：邀请/添加团队成员；
   - `PUT /api/teams/projects/{projectId}/members/{userId}/role`：调整成员角色；
   - `DELETE /api/teams/projects/{projectId}/members/{userId}`：移除团队成员（禁止移除项目唯一 OWNER）。

### 2.2 不可篡改安全审计日志（Immutable Audit Logs）

1. **结构化审计模型与持久化**：
   - 基于 `auth_audit_logs` 表，所有安全事件包含：`eventId`、`userId`、`eventType`（如 `LOGIN_SUCCESS`、`PERMISSION_ELEVATION`、`PROJECT_MEMBER_ADDED` 等）、`ipAddress`、`detailsJson`、`createdAt`；
   - 审计日志只增不减（Append-Only），严格禁止任何更新或物理删除接口。
2. **审计日志查询端点**：
   - `GET /api/system/audit-logs`：支持按 `userId`、`eventType` 过滤，支持基于 `page` 和 `size` 的分页拉取，返回 `AuthAuditLogView` 集合。

### 2.3 灾备快照（Disaster Recovery Snapshot）与幂等还原

1. **标准化备份清单（`BackupManifest`）**：
   - 备份包含系统版本、各表记录计数（Projects、Workspaces、Runs、StepRuns、Artifacts、Approvals、Deployments 等）、工作区归档哈希、清单 SHA-256 校验和以及触发人标识。
2. **备份防篡改校验（`BackupVerifyCommand` & `RestoreVerificationReport`）**：
   - `POST /api/system/backup/verify`：对快照清单与物理归档进行完整性二次校验，检查 SHA-256 散列一致性、版本兼容性与表记录完整度，输出校验报告。
3. **确定性幂等还原（`POST /api/system/backup/restore`）**：
   - 还原动作要求执行前校验；系统采用事务一致性安全校验与基线比对，记录灾备恢复事件，更新灾备还原指标。
4. **灾备触发端点**：
   - `POST /api/system/backup`：触发全量受控快照生成，返回标准化 Manifest。

### 2.4 运维可观测性大盘与 SLO 健康度评测

1. **指标统一聚合（`OpsMetricsSummaryView`）**：
   - `GET /api/system/metrics/summary`：聚合呈现系统状态、活动工作流计数、活动部署数、工作区容量、JVM 内存水位、Prometheus 告警状态与各关键 SLO 达标率；
2. **SLO 健康度计算准则**：
   - 依据工作流执行成功率、P95 响应耗时与活跃告警数，自动判定 SLO 状态为 `HEALTHY`、`DEGRADED` 或 `UNHEALTHY`。

### 2.5 前端团队协作与运维灾备控制台（TeamRbacPanel & OperationsConsolePanel）

1. **TeamRbacPanel**：
   - 遵循 Modern Aesthetic 与 Bento Grid 质感，展示成员列表、角色状态 Badge、权限说明与成员操作；
   - 支持安全邀请与角色变更，防范误踢项目所有者；
2. **OperationsConsolePanel**：
   - 展示 SLO 运行概览指标卡片、内存/工作区图表、灾备快照管理面板与可分页安全审计日志表；
   - 保证暗黑与浅色模式下的极致视觉体验，消灭 AI 塑料味。

---

## 3. 架构影响与后果

- **安全防御边界收敛**：通过 `ProjectRbacRole` 矩阵与 `ResourceOwnershipVerifier`，项目所有资源的操作被严格关锁在角色授权范围内，杜绝了无权限用户的非授权调用；
- **合规审计闭环**：所有关键事件均记录进不可篡改审计流水，支持离线证据包与在线审计检索互为补充；
- **运维与灾备确定性**：系统具备从快照创建、SHA-256 完整性验证到幂等还原的标准流程，配合 Prometheus/Micrometer 实现了端到端可观测性；
- **严格遵守架构铁律**：零新增数据库表，零破坏既有 22 张核心表与 12 个 Flyway 迁移脚本，前端 `page.tsx` 严格保持在 420 行以内。
