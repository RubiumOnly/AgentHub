# ADR-013：Phase 8 端到端验收、性能基准与开源/交付就绪

- **状态**：已采纳（Accepted）
- **日期**：2026-10-15
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §9.12 Phase 8

## 1. 背景与问题陈述

在完成了 Phase 0 至 Phase 7 的模块收敛、受控工作区、编排计划、运行运维、质量门禁、组织 RBAC 与真实 Provider 治理后，AgentHub 核心能力已经就绪。然而，在正式开源与团队交付前，系统仍需解决以下关键问题：

1. **缺乏跨限界上下文的 10 阶段全流程自动化闭环验证**：
   此前各阶段测试重点验证单一模块或两三个子系统的交互（如工作区初始化、计划确认、审批决策、沙箱探针）。缺乏一个统一串联项目接入、受控工作区、模版拓扑解析、Orchestrator 计划与确认、Agent 运行时执行、质量门禁多维评估、Pull Request 草稿生成、部署准入与健康探测、审计流水与证据包导出的端到端集成测试。
2. **缺乏并发吞吐量、事件流延迟与高负载内存边界的基准指标与防衰退断言**：
   系统需要在高并发工作流执行、高频 SSE 广播推送和持续对象分配下建立明确的延迟分布（P50/P95/P99）与内存占用防泄漏断言，防止未来演进中引入性能衰退或无界内存膨胀。
3. **缺乏标准化的外部 Webhook 事件通知契约与防篡改/防重放机制**：
   企业研发工具链（如 GitLab、GitHub、Jenkins 或内部协作系统）需要接收 AgentHub 状态变更通知。需要提供规范的 HMAC-SHA256 签名机制（`X-AgentHub-Signature`）与时间戳容差校验（防重放攻击），并覆盖核心资源生命周期的标准事件。
4. **缺乏稳定发行版本清单（Release Manifest）与最小 SDK 客户端/扩展 SPI 契约**：
   对外交付需要清晰的 API 版本声明、支持的运行模式、稳定的核心资源模型以及插件扩展 SPI 接口（Provider、Tool、AgentDefinition、WorkflowTemplate、DeploymentTemplate），避免将内部实现细节或易变实体暴露给外部调用方。
5. **部署准入防御完整性**：
   明确阻断未通过质量门禁（GATE_FAILED）或被人工拒绝（REJECTED）的代码制品进入部署预览环境，防止不合规代码意外交付。

---

## 2. 决策内容

### 2.1 10 阶段软件交付全生命周期自动化集成校验

在 `Phase8PerformanceBenchmarkAndE2ETest` 中固化了全生命周期的自动化校验链路，覆盖以下 10 个阶段：
1. **Project Onboarding**：创建标准化工程项目，分配受控目录基线；
2. **Workspace JGit Baseline**：初始化 Git 仓库，写入初始源文件并生成权威基线 Commit；
3. **WorkflowTemplate 拓扑解析**：加载标准研发交付模板，完成基于 Kahn 算法的 DAG 拓扑排序；
4. **Orchestrator 计划生成与用户确认**：生成受管变更范围计划，用户确认后冻结 DSL 与输入；
5. **Agent 执行与运行状态流转**：分发执行任务，通过状态机推进 RUNNING 至 SUCCEEDED，记录 StepRun 与事件序列；
6. **质量门禁多维评估**：针对生成制品执行静态检查、测试门禁与安全扫描，生成规范的 QualityGateReport；
7. **Pull Request 草稿创建**：基于基线与变更 Commit 生成标准化 Pull Request 草稿；
8. **受控预览部署与主动探针**：为已通过质量门禁的制品申请分配端口，启动轻量静态预览服务并由主动探针服务校验 HTTP 200 连通性；
9. **审计流水与制品血缘追踪**：记录不可篡改的操作审计日志，校验从 Project -> Run -> Artifact 的完整血缘关系；
10. **脱敏运行证据包导出**：导出携带 SHA-256 校验和、事件流水与模式标识的规范证据包并安全清理现场。

### 2.2 性能基准指标与防御性防衰退断言

通过自动化压测套件确立如下基准并设置防御性门禁：
1. **多线程并发吞吐与延迟基准**：
   - 在 12 线程并发工作流状态推进压测下，验证并发安全性与确定性；
   - 确立 P50、P95 与 P99 耗时指标统计，断言 P95 延迟受控（< 1000ms），保障高并发下状态转换与事件落库平稳。
2. **SSE 事件流广播延迟与单调回放基准**：
   - 验证事件从触发到 SSE 订阅者接收的广播物理延迟受控（< 60ms）；
   - 验证基于 `Last-Event-ID` 的单调递增增量事件回放，确保网络重连时事件无丢失且严格保序。
3. **高负载内存占用稳定性与有界性**：
   - 在循环创建 40 组分层上下文记忆与事件对象的负载下执行垃圾回收与堆内存对比；
   - 断言有效堆内存增量在受控边界内（< 100MB），验证长周期执行不存在静态对象泄露或无界集合堆积。

### 2.3 Webhook 事件通知契约与安全签名规约

1. **标准化 Webhook 事件枚举（`WebhookEventType`）**：
   - 涵盖 22 种核心生命周期事件：项目创建、工作区基线就绪、工作流运行（started/completed/failed/cancelled/rejected）、步骤运行（started/completed/failed/rework_requested）、审批决策（requested/decided）、制品交付（created/evaluated/accepted/rejected/reverted）以及部署环境（created/healthy/failed/stopped）。
2. **规范 Payload 载荷（`WebhookPayload`）**：
   - 统一包含 `eventId`、`eventType`、`occurredAt`、`resourceType`、`resourceId`、`projectId` 以及领域特定的 `data` 映射。
3. **HMAC-SHA256 签名机制与防重放校验（`WebhookSignatureVerifier`）**：
   - 使用请求头 `X-AgentHub-Signature` 携带 `t={timestamp},v1={hmac_sha256_hex}`；
   - 签名计算内容为 `{timestamp}.{payloadJson}`；
   - 校验器内置可配置的时间戳容差（默认 300 秒），有效防御网络重放攻击与篡改。

### 2.4 稳定发行清单与最小 SDK 客户端 / 扩展 SPI 契约

1. **统一发行元数据清单（`AgentHubReleaseManifest`）**：
   - 版本号固化为 `1.0.0-RELEASE`，API 契约基线 `v1`；
   - 明确支持的三种运行模式：`DEMO_SIMULATED`、`LIVE_SELF_HOSTED`、`LIVE_TEAM`；
   - 规范 8 个稳定限界上下文与 11 个稳定核心资源对象（`Project`, `Workspace`, `WorkflowDefinition`, `WorkflowRun`, `StepRun`, `Artifact`, `QualityGateReport`, `Approval`, `Deployment`, `TokenUsage`, `AuditLog`）；
   - 提供 v2 到 v1 的字段迁移指引与平台兼容性矩阵。
2. **最小 SDK 客户端接口契约（`com.agenthub.shared.contract.sdk`）**：
   - `AgentHubProjectClient`：项目与工作区只读/管理契约；
   - `AgentHubRunClient`：运行启动、状态查询与取消契约；
   - `AgentHubArtifactClient`：制品行血缘、质量门禁与 PR 草稿契约；
   - `AgentHubApprovalClient`：人工审查待办与审批决策契约；
   - `AgentHubDeploymentClient`：预览部署创建、主动探测与停止契约。
3. **第三方扩展 SPI 契约（`com.agenthub.shared.contract.extension`）**：
   - `ProviderExtensionContract`：大模型提供商扩展；
   - `ToolExtensionContract`：智能体受控工具扩展；
   - `AgentDefinitionExtensionContract`：智能体角色与能力定义扩展；
   - `WorkflowTemplateExtensionContract`：工作流模板与 DAG 节点编排扩展；
   - `DeploymentTemplateExtensionContract`：沙箱环境部署生命周期模版扩展。
4. **元数据 REST 统一暴露**：
   - 通过 `GET /api/system/contracts/v1` 暴露完整的 releaseManifest、webhookSpecification、sdkSpecification 以及 extensionContracts。

### 2.5 质量门禁准入阻断

严格实施部署准入防御逻辑：
- 当制品评估状态为 `GATE_FAILED`，或审批决策为 `REJECTED` 时；
- `DeploymentApplication.createDeployment` 抛出 `BusinessException(ErrorCode.PARAM_ERROR)`，拒绝分配物理端口与启动进程。

---

## 3. 结果与影响

### 积极效果
1. **完备的集成防御网**：10 阶段全流程与 7 个端到端基准测试用例建立了针对回归与漂移的高强度防御。
2. **标准化对接协议**：Webhook HMAC-SHA256 与 SDK 契约使外部系统可以可靠、安全地与 AgentHub 集成。
3. **架构零破坏与零膨胀**：所有扩展严格在现有的 22 张数据库表、12 个 Flyway 迁移脚本与 18 个 REST Controller 边界内完成，没有引入空壳消息中间件或多余实体。

### 权衡与边界
- 目前本地端到端测试运行在 H2 MySQL 兼容模式下；真实多节点生产集群、物理网络断连及大规模并发压力测试仍需由远端持续集成流水线结合专用测试集群承载。
