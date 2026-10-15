# ADR-010：Phase 5 质量门禁、审查与受控交付产品化

- **状态**：已采纳（Accepted）
- **日期**：2026-10-15
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §7 Phase 5（质量门禁、审查和交付产品化）

## 1. 背景与问题陈述

在 Phase 4 完成单一状态图谱、事务性发件箱（Transactional Outbox）、三层分层上下文记忆与产物血缘回滚后，AgentHub 具备了高可靠的运行运维与快照级版本溯源能力。然而，为了真正达成“面向小型研发团队的、可审查的 AI 软件变更交付平台”这一产品定位，平台交付的必须是**经过自动化防御验证的、高质量的“可接受变更（Acceptable Change）”**，而非不可信的“原始代码生成段”。当前仍面临以下结构性痛点：

1. **质量门禁缺乏策略化与客观防御约束**：
   过去的测试与构建分散在各个工作流节点中，缺乏系统级的统一质量门禁策略（Quality Gate Policy）。若无强制的门禁引擎，前端易受误操作影响或伪造通过标记；没有在交付前对变更代码进行硬编码密钥/敏感信息扫描（Secret Leak Scan）、代码规模预算（Diff Budget）与规范性静态检查。
2. **代码审查缺乏多维透视与细粒度干预手段**：
   在审查大型代码变更时，缺乏按文件路径、风险等级（LOW/MEDIUM/HIGH）、执行 Agent 角色（如 backend-developer vs security-reviewer）和测试覆盖的结构化过滤视图；当用户发现某一个步骤存在逻辑偏差或某个单一文件引入错误时，只能全盘接受或全盘回滚，缺乏针对单一 Step 提出反馈并触发“重做（Rework）”，以及“仅回滚单个问题文件（Single-file Revert）”的非破坏性微调能力。
3. **预览部署准入与资源全生命周期回收不严密**：
   沙箱预览部署若允许未经验收的 Artifact 或未通过门禁的构建直接启动，会导致恶意/畸形代码进入运行态；在容器或本地进程停止、崩溃或达到 TTL 限制时，若端口未归还、进程未杀灭、临时资源未回收，将导致单机宿主资源泄漏与端口冲突。
4. **外部 Git 交付缺乏受控 PR 草稿与只读审查能力**：
   将 AI 变更交付给外部 GitHub/GitLab 仓库时，直接强制推送主分支存在巨大安全隐患；缺乏受控生成结构化 PR 草稿（包含变更动机、模块影响、质量门禁证明与离线测试报告）的标准交付规范，且外部写操作缺乏权限保护与脱敏回执。
5. **合规审计证据包缺乏确定性离线归档**：
   企业的安全合规部门需要能够对每一次 AI 交付进行脱机审查（Offline Audit），包括完整的 Run 快照、JGit 逐行 Diff、质量门禁评测细则、审批操作日志与 SHA-256 防篡改校验指纹。

---

## 2. 决策内容

### 2.1 质量门禁策略与多维规则评估引擎（QualityGateEngine）

1. **核心门禁规则矩阵（QualityGateRuleType）**：
   - `BUILD_VERIFICATION`：后端 Maven 编译 / 前端 Next.js 静态打包校验（构建失败一票否决）；
   - `UNIT_TEST_REGRESSION`：回归测试套件通过率验证（要求 100% 绿灯通过，失败断言与异常均被捕获）；
   - `STATIC_ANALYSIS_GUARD`：代码格式、命名规范、圈复杂度及架构分层守卫（阻断违规穿透）；
   - `SECRET_LEAK_SCAN`：代码安全扫描，利用敏感模式正则检测是否包含硬编码 API Key、私钥、明文密码或 Token；
   - `DIFF_BUDGET_GUARD`：单次变更规模防御（对增删行数与变更文件数施加安全阈值，防止单次变更失控膨胀）。
2. **确定性质量门禁报告（QualityGateReport）**：
   - 门禁评测结果包含规则细项（`QualityGateCheckItem`：通过状态、得分、耗时、详细原因），计算加权质量评分（0～100 分）；
   - 每次评测自动计算 SHA-256 签名，并持久化到 `ArtifactEntity` 的元数据与 `run_events` 审计流水中。
3. **不可伪造性（Anti-Forgery Enforcement）**：
   - 门禁通过状态严格由后端 `QualityGateEngine` 判定；任何门禁未通过的产物，其 `reviewStatus` 标记为 `GATE_FAILED`；
   - 前端按钮无法通过任意接口绕过门禁，未通过门禁的产物严禁流转至受控部署与发布。

### 2.2 多维度审查与细粒度回退/重做机制

1. **多维度审查过滤（Structured Diff & Lineage Filtering）**：
   - 审查接口支持按文件路径（`filePath`）、风险级别（`riskLevel`）、责任 Agent 角色（`agentRole`）及门禁状态进行组合过滤；
   - 单个文件差异携带风险评级与修改性质（ADDED/MODIFIED/DELETED），支持精确代码行级审查。
2. **Step 级审查意见与受控重做（`reworkStep`）**：
   - 用户可针对特定 Step 提供反馈指导意见（`reworkFeedback`）；
   - 系统将该 StepRun 状态回退为 `PENDING`，自增 `attempt`，将用户意见压入步骤上下文记忆中，记录 `STEP_REWORK_REQUESTED` 事件并重新唤醒执行引擎。
3. **单文件非破坏性回滚（`revertSingleFile`）**：
   - 在多文件修改中，用户可仅选择单个瑕疵文件进行回滚；
   - `JGitWorkspaceManager` 利用 Git Checkout 指针将指定相对路径的文件恢复至基线 Commit 版本（若为新增文件则彻底删除），其他文件的变更丝毫不受影响；
   - 提交原子回滚 Commit 并记录 `FILE_REVERTED` 审计条目。
4. **Run 级整体验收驳回（`rejectRun`）**：
   - 一键驳回当前 Run 的所有变更，工作区强一致重置至基线 Commit，将工作流状态跃迁至 `REJECTED`，记录 `RUN_REJECTED` 审计流水。

### 2.3 受控沙箱预览部署与全生命周期证据

1. **严格部署准入约束**：
   - `DeploymentApplicationService` 严格强校验：所绑定的 Artifact 必须为已接受状态（`ACCEPTED` 或 `APPROVED`），且关联的质量门禁为 `PASSED`；任何处于 `REJECTED`、`REVERTED` 或 `GATE_FAILED` 的产物坚决阻断部署（抛出 `400 PARAM_ERROR`）。
2. **完整受控生命周期轨迹**：
   - 全程跟踪 `BUILD -> START -> HEALTH_CHECK -> RUNNING -> STOP -> CLEANUP` 状态跃迁；
   - 无论是正常下线、主动调用 `stopDeployment`，还是后台看门狗触发 TTL 超时，**100% 执行资源回收**：归还分配的 TCP 端口至端口池、销毁本地进程/容器、清理临时工作目录。
3. **主动健康检查探针（Health Check Probe）**：
   - 提供 `POST /api/sandbox/deployments/{deployId}/probe` 端点，执行真实 HTTP GET 探活并返回探针往返耗时与 HTTP 状态码。

### 2.4 远程 Git PR 草稿规范与只读审查能力

1. **Pull Request 草稿规格生成（`GET /api/audit/artifacts/{artifactId}/pr-draft`）**：
   - 自动组装标准 PR 规范（`PullRequestDraftView`）：标题、关联 Run、来源分支、目标主分支、多维变更摘要 Markdown、质量门禁证明、安全扫描回执与离线测试说明；
2. **写权限防御与只读脱敏回执**：
   - 严格禁止在没有用户明确授权或安全凭据的情况下擅自向远程主分支推送代码；若未配置具有写权限的 secretRef，系统生成脱敏的 PR 草稿回执与本地审查补丁，确保外部零越权风险。

### 2.5 确定性离线审计证据包导出（Run Evidence Package）

1. **完整证据链归档（`GET /api/executions/runs/{runId}/evidence/export`）**：
   - 统一导出涵盖：Run 规格、DAG 节点拓扑、Step 执行历史、JGit 逐行 Diff、产物血缘谱系、质量门禁多维检测细则、审批决议链、沙箱部署轨迹与敏感信息脱敏（`SecretMasker`）；
   - 输出符合 RFC-8259 规范的确定性排序 JSON，并生成根级 SHA-256 校验和（`checksumSha256`），为安全合规提供脱机防篡改凭证。

---

## 3. 架构影响与后果

- **一致性与安全性**：彻底杜绝“未通过门禁却被部署”和“前端随意修改审批状态”的隐患，所有交付动作均有法可依、有据可查；
- **操作粒度细化**：用户获得单文件回滚、Step 重做与 PR 草稿导出的精细化控制权，大幅降低多智能体协作产出代码时的返工成本；
- **资源确定性回收**：沙箱预览部署的端口分配与进程生命周期形成闭环，根治长时间运行后的端口泄漏与僵尸进程；
- **零新表零破坏**：继续严格在既有 22 张核心表、12 个 Flyway 迁移脚本与 18 个 Controller 的边界内扩展，不引入任何数据库迁移破坏与兼容性风险。
