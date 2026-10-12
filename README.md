# AgentHub

AgentHub 是面向软件研发团队的有状态多 Agent 变更交付平台。它把需求、Agent 计划、受控代码工作区、DAG 执行、人工审批、Diff、质量门禁和预览交付串成一条可追溯链路。

当前基线技术栈：Java 17、Spring Boot 3.3.4、MySQL 8.0、Flyway、JGit、Next.js 14.2.13 和 Docker Compose。系统采用模块化单体，暂不以微服务或公网任意代码执行为目标。

## 产品主线

```text
登录 -> 接入 Project -> 创建 Workspace -> 选择 Workflow Template
     -> Orchestrator 生成计划 -> 用户确认
     -> Agent 执行 -> Diff/测试/风险审查 -> 人工审批
     -> 受控预览或交付物 -> 导出 Run Evidence
```

产品主线中的事实来源是后端的 Project、Workspace、WorkflowDefinition、WorkflowRun、StepRun、RunEvent、Artifact、Approval 和 Deployment。前端只负责投影和用户操作，不自行制造运行成功状态。

## 运行模式

| 模式 | 用途 | 事实边界 |
| --- | --- | --- |
| `DEMO_SIMULATED` | 无密钥演示、CI 和确定性回归 | Mock 输出和模拟标识，不能表述为真实模型或生产部署 |
| `LIVE_SELF_HOSTED` | 个人或团队自托管 | 使用已配置的真实 Provider 和受控模板，默认单节点 |
| `LIVE_TEAM` | 组织化团队使用 | 需要组织角色、预算、审计、备份和 SLO 能力完成后启用 |

模拟行为必须在 UI、API 和证据包中显式标识。真实 Provider 失败不能静默伪装成成功。

## 产品简报 (Product Brief)

- **目标用户**：3～10 人、已有 Git 仓库与代码评审习惯、需要交付内部系统或业务服务的研发团队。
- **首个项目模板**：受控 Spring Boot 3.x / Next.js 代码仓库变更模板（首批聚焦“修复一个带测试的后端缺陷”与“为现有 Spring Boot 服务新增一个小接口并更新测试”，预览模板支持 `STATIC_WEB_TEMPLATE` 与 `JAVA_SPRING_TEMPLATE`）。
- **明确不做的能力（Out-of-Scope）**：不承诺公网不可信代码执行、任意 Shell 平台、通用 Agent 市场、物理分库多租户（Schema/DB-per-Tenant）、移动端和无限制 Provider 矩阵；IM/群聊仅作为协作扩展，不作为产品主线。
- **数据保留策略**：Run/Step/Event/Approval/Artifact/TokenUsage/AuthAudit 持久化于 MySQL 8.0；仅当 `projects.status = 'ARCHIVED'` 且无活跃任务和锁时才允许经数据库对账清理归档工作区；冷备默认保留 14 天并校验 `SHA256SUMS`；SSE `stream-ticket` 内存 TTL 60 秒且单次消费即焚。
- **安全边界**：`prod` 模式强制要求外部注入 `AGENTHUB_AUTH_SECRET`（>=32 字符）与 `ACTUATOR_PROMETHEUS_TOKEN`，禁用 `dev-token-*`、`X-User-Id` 伪造头、固定 `user-1` 旁路与 `/api/workspace/*` 旧接口；Workspace 仅接收服务端 `workspaceId` 与受检相对路径（单文件写上限 10MB / 预览 2MB）；部署仅允许 `ACCEPTED` 产物与白名单命令。

## 当前已验证的能力

- 外部配置的认证密钥、Bearer/Cookie 鉴权、SSE 短时票据和资源归属校验。
- 版本化 Workflow DSL、DAG 调度、数据库租约、进程重启恢复和持久化 SSE 回放。
- Provider SPI、动态路由、熔断、降级标识、Token 用量和凭据脱敏。
- 受控 Workspace、JGit 基线/快照/Diff/回滚、Artifact 审查和 Deployment 生命周期。
- Next.js 控制台、Prometheus 指标、备份恢复脚本和脱敏 Run Evidence Bundle。

“代码完成”“自动化测试通过”和“目标环境验证通过”是三种不同证据，不能互相替代。当前能力边界以 [`docs/CURRENT_CAPABILITY_BOUNDARIES.md`](docs/CURRENT_CAPABILITY_BOUNDARIES.md) 为准。

- **当前验证基线（2026-10-12）**：后端 `mvn clean test` 共 53 个测试类、310 个测试用例通过（Windows 11 / OpenJDK 17 / H2 MySQL Mode，以及 GitHub Actions `ubuntu-latest` + `mysql:8.0` 容器门禁）；前端 `npx tsc --noEmit` 与 `npm run build`（Node.js 20）通过；`docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet` 通过。

## 本地验证

```powershell
Set-Location agenthub-backend
mvn clean test

Set-Location ..\agenthub-frontend
npm ci
npx tsc --noEmit
npm run build

Set-Location ..
docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet
```

真实 MySQL、镜像、Compose 启停、浏览器和 Provider 验证应按照 CI 与 E2E 文档执行，不能仅凭静态构建结果宣称上线。

## 文档导航

- [产品化长期计划](docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md)：唯一有效的长期路线，包含 Product Brief、瘦身清单、AOCI 长上下文治理和 Phase 0～8。
- [架构说明](docs/ARCHITECTURE.md)：模块、核心资源与事件词汇表、数据库表矩阵、数据流、状态机和扩展边界。
- [API 契约](docs/API.md)：REST、SSE、错误、幂等、资源归属约定以及 API/前端调用矩阵。
- [当前能力边界](docs/CURRENT_CAPABILITY_BOUNDARIES.md)：已验证能力、模拟模式、Provider/Runtime/Sandbox 能力矩阵和未实现范围。
- [E2E 演练指南](docs/E2E_DEMO_GUIDE.md)：Bash/PowerShell 运行和证据包导出步骤。
- [运维告警规则](docs/ops/ALERTING_RULES.md) 与 [灾备手册](docs/ops/DISASTER_RECOVERY_RUNBOOK.md)：目标环境运维参考。
- [ADR](docs/adr/ADR-001-modular-monolith-architecture.md)：架构决策记录索引从 ADR-001 到 ADR-005。

技术答辩材料不再作为独立事实来源；需要解释架构权衡时，以架构说明、ADR、能力边界和实际证据为准。
