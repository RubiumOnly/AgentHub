# ADR-007：Phase 2 项目接入向导、受控工作区安全策略与版本化上下文源

- **状态**：已采纳（Accepted）
- **日期**：2026-10-13
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §6 Phase 2（项目接入与受控工作区产品化）

## 1. 背景与问题陈述

在 Phase 1 实现了模块收敛与 API 契约 v1 后，系统的项目与工作区生命周期仍存在以下产品化与安全性短板：

1. **项目接入缺乏受控标准化向导**：早期项目创建仅为简单的内存/DB 记录新增，缺乏对本地代码库或远程 Git 仓库的合法性审查、凭据有效性探测、技术栈扫描与初始架构防御规则注入。
2. **凭据安全与敏感路径防护薄弱**：存在直接在客户端传递明文 Token（如 GitHub PAT `ghp_...`）的安全隐患；且工作区路径缺少对宿主操作系统绝对路径（如 `C:\Windows`、`/etc/passwd`）和路径穿越（`../../`）的统一结构化防御拦截。
3. **工作区配额与策略熔断缺失**：缺乏对单文件大小、工作区总容量、文件总数、运行耗时以及外网访问权限的统一配额守卫，极端输入或异常 Agent 递归可能导致宿主磁盘与资源耗尽。
4. **上下文来源（Context Source）未版本化**：Agent 执行所需的认知输入（AOCI 认知基线、项目工程规约、构建配置、测试防御规约）散落在不同地方，缺乏不可篡改的 SHA-256 内容指纹以及与 Git Baseline Commit 的强绑定。
5. **受控工作区生命周期能力不完备**：缺少重新同步（resync）、基线变更（changeBaseline）、放弃本地变更（discardWorkspace）、恢复历史快照（restoreSnapshot）等关键受控操作，且危险操作未强制用户确认（`confirmed=true`）与安全审计。

## 2. 决策内容

### 2.1 7 步受控项目接入向导（Controlled Onboarding Wizard）

引入不可跳过、严格状态机推进的 7 步接入流程：
1. `CONNECT_SOURCE`：验证代码源配置（本地相对安全目录或合法 Git 远程仓库）；
2. `VERIFY_PERMISSIONS`：验证凭据引用 `secretRef`（严禁明文 Token，强制别名引用）与读取/写入权限；
3. `SELECT_DEFAULT_BRANCH`：确认默认拉取基线分支；
4. `CREATE_WORKSPACE`：分配隔离工作区与独立特性分支 `feature/agenthub-{workspaceId}`；
5. `SCAN_ENVIRONMENT`：自动探测项目编程语言、框架、构建工具、测试框架及安全边界；
6. `GENERATE_PROJECT_RULES`：根据扫描结果生成初始 `PROJECT_RULES.md` 工程与架构防御规约；
7. `CREATE_INITIAL_BASELINE`：在 JGit 仓库中固化初始基线提交，在持久化存储中注册初始版本化 Context Source，确权完成。

向导提供失败步骤重试（`retryStep`）与安全中止回滚（`abortOnboarding`）能力，确保脏现场彻底清理。

### 2.2 受控工作区安全策略与 Quota Guard

1. **凭据脱敏拦截**：`SecretMasker` 增强对 GitHub PAT（`ghp_`、`github_pat_`）及主流云厂商密钥的全链路正向与逆向脱敏，任何在请求参数中直接传递明文 Token 的行为均被 `PARAM_ERROR` 拒绝。
2. **路径防穿越与防逃逸**：`GitSourceConfig` 严禁指定宿主绝对路径（盘符、根目录）与包含 `..` 的路径穿越，必须限定在受控应用根目录内。
3. **`WorkspaceQuotaGuard` 配额熔断**：
   - 默认总容量上限：500 MB；
   - 默认文件数上限：5,000 个；
   - 写入单文件上限：10 MB；文本预览上限：2 MB；
   - 执行持续时间上限：3,600 秒；
   - 并发锁上限：单工作区互斥独占；
   - 网络访问控制：按策略严格拦截未经授权的外网连接；
   - 配额超限统一抛出 `ErrorCode.BLOCKED_POLICY (3012)`。

### 2.3 版本化上下文源（Versioned Context Sources）

1. **类型矩阵**：
   - `AOCI_COGNITION`：AOCI 软件工程认知基线；
   - `PROJECT_RULES`：项目工程规约与 DDD 架构分层守卫；
   - `BUILD_CONFIG`：构建与依赖配置（`pom.xml`, `package.json` 等）；
   - `TEST_CONVENTIONS`：测试防御矩阵与验证标准。
2. **指纹与绑定**：每个上下文源均计算确定性 SHA-256 内容指纹（`contentFingerprint`），并与创建时的 Git Baseline Commit 强绑定。
3. **零模式变更复用**：利用已有的 `artifacts` 表和持久化模型存储版本化上下文源，不新增冗余表结构。

### 2.4 受控工作区生命周期运维与多租户审计

1. 扩展 `WorkspaceApplication` 核心接口：
   - `resyncWorkspace(workspaceId)`：将工作区拉回至基线 Commit；
   - `changeBaseline(workspaceId, targetCommitHash, confirmed)`：变更基线（强制 `confirmed=true`）；
   - `discardWorkspace(workspaceId, confirmed, reason)`：放弃本地未提交变更（强制 `confirmed=true`）；
   - `restoreSnapshot(workspaceId, snapshotCommitOrTag, confirmed)`：恢复指定快照（强制 `confirmed=true`）；
   - `getWorkspaceQuota(workspaceId)`：实时聚合工作区容量、文件数与配额使用情况。
2. **多租户与鉴权守卫**：所有工作区与向导操作均经由 `ResourceAccessGuard` 和 `ResourceOwnershipVerifier` 校验所有者归属，拦截任何跨租户越权操作。
3. **审计留痕**：关键生命周期变更均经由 `AuthAuditService` 异步持久化至 `auth_audit_logs` 表。

## 3. 架构影响与实施后果

- **正面效果**：
  - 彻底终结非受控工作区随意挂载宿主敏感目录的安全隐患；
  - 接入过程结构化、可视化、可重试、可审计；
  - 上下文源版本化为后续 Agent 提供不可篡改的代码与架构基准；
  - 严格遵守 `Phase9BaselineAndGovernanceGuardTest`：控制器数保持 18 个，Flyway 保持 12 个迁移，表数量保持 22 张。
- **限制与后续工作**：
  - 远端 Git clone 在无网络或 Mock 环境下优雅回退至本地模板初始化；
  - Phase 3 将基于已版本化的 Context Source 接入 Orchestrator 计划生成与用户审查确认。
