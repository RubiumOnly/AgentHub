# AgentHub 产品化长期演进计划

> 版本：v3.0
> 基线日期：2026-10-12
> 基线提交：`d245597`（Phase F 加固完成）
> 计划跨度：约 18～24 个月；除非产品定位、部署边界或安全假设发生变化，不重新起草计划，只在本文件的阶段记录中更新事实。
> 目标：把 AgentHub 从“功能和工程能力齐全的多 Agent 演示/验证平台”推进为“面向小型研发团队的、可审查的 AI 软件变更交付产品”。

本文只规划后续工作，不代表当前功能已经按本文实施。当前版本的“代码已完成”“自动化测试通过”“目标环境验证通过”必须分别记录，任何一项都不能替代另外两项。

## 1. 当前基线与客观判断

### 1.1 已有证据

当前仓库已经完成上一份长期计划的 Phase A～F，主链路包含身份、项目、工作区、Provider/Runtime、DAG、Run/Step/Event、SSE 回放、人工审批、JGit Diff、受控预览、Prometheus、备份恢复和脱敏运行证据包。当前可观察到的仓库规模为：

- 后端生产 Java 文件 276 个，测试类 53 个；`mvn clean test`（2026-10-12，Windows 11 / OpenJDK 17 / H2 MySQL Mode）实测汇总为 310 个测试用例、0 failures、0 errors、0 skipped。
- 前端源码文件 21 个（`agenthub-frontend/src`，含 20 个 `.ts`/`.tsx` 文件与 1 个 `.css` 全局样式文件），包含 Next.js 14 控制台、DAG、终端、Diff、审批和预览面板；`npx tsc --noEmit` 与 `npm run build`（2026-10-12，Node.js 20）构建通过。
- Java、TypeScript/TSX 和前端源码合计约 3.85 万行；Flyway 迁移已到 V12（共 22 张业务表）。
- CI 已包含 Maven 测试、真实 MySQL/Flyway 关键测试、Phase 0 债务冻结与基线守卫、前端类型检查和构建、Compose 配置、Prometheus 规则、镜像非 root 检查及灾备脚本演练。
- `docs/CURRENT_CAPABILITY_BOUNDARIES.md` 已经明确标识模拟模式、多节点广播、对象存储、强隔离沙箱和细粒度 RBAC 等尚未实现边界。

以上数字是当前工作树的静态和历史运行证据；后续代码变更后必须重新采集，不能直接沿用旧文档中的测试数字。

### 1.2 当前仍然存在的产品化问题

项目已经有较强的工程骨架，但仍然有“能力很多、产品主线不够窄、真实来源不够统一、历史兼容代码较多”的问题。以下问题必须进入后续计划，而不是用新增功能掩盖：

1. **系统认知资产刚完成切换**：原根目录 `ARCHITECTURE_INDEX.md` 曾记录早期 13 个测试和旧模块结构，现已删除；`CHANGELOG.md` 仍保留部分历史口径。AOCI 的 Managed Scope、Receipt、Baseline、漂移和恢复证据尚未完成接入，不能把当前 Markdown 摘要当作长期 Agent 上下文的唯一来源。
2. **默认数据仍污染产品语义**：迁移和部分应用服务仍可见 `user-1`、`proj-default`、`run-default`、`system` 等默认值。即使生产鉴权已阻断若干旁路，真实产品仍会被演示种子和隐式默认上下文污染。
3. **旧新接口并存**：`/api/workspace`、`WorkflowAndDiffController`、`RunAliasController`、`WorkspaceApplication` 等兼容路径仍在代码中；前端 `WorkflowCanvas.tsx` 仍存在直接访问 `localhost:8080` 的路径。双轨接口会持续制造授权、契约和测试分叉。
4. **模块深度不足且应用服务过大**：`ExecutionApplicationService`、`DagExecutionEngine`、`ConversationApplicationService`、`DeploymentApplicationService` 和 `JGitWorkspaceManager` 都已经成为数百行的大模块。它们内部有价值，但接口、事务、调度、序列化、审计和错误处理混在一起，后续维护会越来越依赖整文件阅读。
5. **Agent 能力仍偏适配器集合**：Provider、Runtime、CLI、Mock 和安全降级路径较多，但用户还不能以稳定的“模板、工具权限、上下文预算、质量门禁、成本预算”来复用一个 Agent 工作单元。
6. **多 Agent 仍容易被误解为 IM 产品**：Team、Conversation、Mention 和消息序列是基础能力，却不是当前最有价值的用户结果。产品应围绕“研发变更交付”组织协作，而不是继续扩展飞书/微信式社交功能。
7. **真实生产边界尚未被产品契约固定**：本地 Process、Docker CLI、静态预览和多种 Provider 仍需用配置组合。产品需要明确支持的模板、部署环境、数据保留、成本上限和故障处理，否则每一次演示都可能产生不同结论。

### 1.3 本计划的战略结论

未来的核心不是继续堆 Provider、节点类型、聊天特效或新的部署目标，而是收敛到一条可重复的价值链：

> 连接一个受控代码项目 → 选择一个研发变更模板 → Orchestrator 生成并解释计划 → Agent 在受控工作区执行 → 用户审查 Diff、测试和风险 → 审批后生成可访问预览或交付物 → 导出可追溯证据。

这条链路必须同时满足 Java 后端工程师的技术深度和 Agent 应用开发的产品深度：状态、事务、租约、幂等、限流、成本、上下文和安全都要落到可验证的运行行为，而不是只存在于白皮书中。

## 2. 长期产品定义

### 2.1 首个目标用户和产品承诺

首个目标用户是 3～10 人、需要交付内部系统或业务服务的研发团队。团队已有 Git 仓库和代码评审习惯，希望把一次“需求到可审查变更”的过程交给可控的多 Agent 流程，同时保留人工决策权。

产品承诺限定为：

- Agent 可以解释它准备做什么、使用哪些工具、需要多少预算，并在执行前接受用户确认。
- 每次运行都有不可变的项目、工作区、工作流、Provider、输入和权限快照。
- 每个文件变更、测试结果、审批决定、部署状态和 Provider 消耗都可以回溯到同一次 Run。
- 外部模型失败、进程重启、SSE 断线、审批等待和部署失败都显示真实状态，并允许恢复、重试、取消或回滚。
- 任何模拟或降级行为都带有明确标识，不进入“真实成功”统计。

首个版本不承诺公网不可信代码执行、任意 Shell 平台、通用 Agent 市场、物理隔离多租户、移动端和无限 Provider 矩阵。

### 2.2 产品模式

产品长期保留三种明确模式，并在页面、API、证据包和运维指标中使用同一命名：

| 模式 | 用途 | 允许的事实 | 禁止的表述 |
| --- | --- | --- | --- |
| `DEMO_SIMULATED` | 离线评审、CI、无密钥体验 | Mock 输出、确定性工作区、模拟部署 | 真实模型调用、生产性能、真实部署成功 |
| `LIVE_SELF_HOSTED` | 个人或团队自托管 | 已配置的真实 Provider、受控模板、单节点或明确的 Worker | 未验证的集群能力、强隔离承诺 |
| `LIVE_TEAM` | 受控研发团队长期使用 | 组织、角色、预算、审计、备份、SLO | 公网任意租户和无限制执行 |

模式不能依赖“是否出现过一条 Token 记录”这类事后猜测，而应在 Run 创建时固化执行策略，并在完成时用 Provider、Runtime、工具和部署证据核对。

### 2.3 关键产品指标

从 Phase 0 起持续采集下列指标。指标只服务于产品取舍，不用于制造简历数字：

- **交付成功率**：Run 在质量门禁、审批和部署验证后产生可接受 Artifact 的比例。
- **人工干预率**：每次 Run 被用户暂停、重试、修改计划或回滚的比例及原因。
- **从目标到可审查 Diff 的时间**：不含 Provider 网络等待时，统计执行内核耗时；另记录端到端时间。
- **真实 Provider 占比与降级率**：Mock、Provider 失败、Fallback 和取消必须分开统计。
- **单位交付成本**：按输入/输出 Token、Provider 费用、执行时长和沙箱资源统计。
- **恢复成功率**：进程重启、租约过期、SSE 断线、Provider 暂时失败后的可恢复比例。
- **产品可信度指标**：假数据命中次数、未关联 Artifact 的“成功”次数、过期索引、过期证据和资源越权拒绝数，目标均为零。

## 3. 长期不变量与架构原则

1. **一个用户动作只能有一个事实来源**：Run、Step、Approval、Artifact、Deployment 的状态由后端持久化模型决定，前端只是投影。
2. **一个领域只保留一个生产接口**：兼容接口可以在过渡期只读或返回迁移提示，但不得继续产生新的业务状态。
3. **一次执行是不可变快照**：工作流 DSL、Prompt/工具版本、Provider 策略、预算、权限、工作区基线和输入在 Run 创建后不可被静默替换。
4. **异步操作使用 at-least-once + 幂等**：不宣称分布式 exactly-once；每个副作用都有幂等键、尝试号、超时和补偿记录。
5. **上下文是受治理的产品资源**：系统提示、项目规则、代码索引、数据库 Schema、Run 事件、摘要和工具输出必须有来源、版本、预算和安全级别。
6. **模拟和真实行为隔离**：Mock 只能用于 Demo/Test profile 或用户明确选择的模拟模板，不能作为真实 Provider 全局静默兜底。
7. **默认安全关闭**：没有身份、预算、项目归属、工具权限、受控模板或健康证据时，Run 必须停在可解释状态。
8. **继续保持模块化单体**：在达到明确的吞吐、隔离或独立发布门槛之前，不拆成微服务；优先加深模块接口，减少跨模块调用和重复状态机。

## 4. 瘦身与架构收敛策略

### 4.1 目标不是减少行数，而是减少认知和行为分叉

38.5k 行源码本身不是问题。真正的风险是一个概念有多个名称、多个接口、多个默认值和多个恢复逻辑。瘦身验收以以下结果为准：

- Project、Workspace、Workflow、Run、Step、Event、Artifact、Approval、Deployment、Agent Policy 各自只有一个生产模型和一个应用入口。
- 旧接口在生产 profile 不再注册；兼容代码集中在一个 migration 包并有删除日期。
- 大模块按用例和事务边界拆成深模块：调用者只知道少量命令/查询接口，锁、状态、重试、审计和持久化细节留在实现内部。
- 前端不再由一个页面和一个 700 行 API 文件管理全部状态；按资源和用例拆分查询、命令、SSE、错误和缓存策略。
- 任何 Provider/Runtime/Sandbox 适配器都必须有真实使用者、契约测试和能力声明，否则进入候选删除清单。

### 4.2 保留、合并、隔离和删除

| 决策 | 当前对象 | 处理方向 |
| --- | --- | --- |
| 保留并加深 | Identity、Project/Workspace、Workflow、Execution、Audit、Sandbox、Provider/Runtime | 作为产品主链路；补齐契约、预算、策略和操作体验 |
| 合并 | `com.agenthub.domain.*` 原型模型与现有模块模型；`WorkflowEngineService`、`ExecutionScheduler`、`DagExecutionEngine`；多套 Workspace Lock 入口 | 先迁移调用者，再删除旧实现；不再增加平行抽象 |
| 隔离 | Team、Conversation、Mention、IM、Mock Runtime、CLI 适配器 | 作为协作扩展、测试模式或受控插件，不阻塞核心交付 |
| 收缩 | Provider 适配器、Sandbox 目标、部署命令类型 | 首个产品只支持经验证的 OpenAI-compatible/Anthropic 或本地 Ollama，以及静态 Web 和一个明确服务模板；其余只保留 SPI 和实验标识 |
| 删除或下线 | `/api/workspace`、`WorkflowAndDiffController`、`RunAliasController`、`WorkspaceApplication`、固定默认项目/用户、前端 localhost 直连 | 先加监控和迁移提示，连续一个版本无生产调用后移除；删除前补契约回归 |
| 重新定位 | 已删除的手工 `ARCHITECTURE_INDEX.md`、历史 Changelog、声称“终局”的白皮书段落 | 人类文档只描述当前事实；机器可消费认知交给受治理的 AOCI 资产 |

### 4.3 反膨胀规则

- 新增一个 Provider、节点类型或部署模板，必须同时提交真实用户旅程、能力声明、契约测试、成本/权限说明和删除条件。
- 不为“以后可能拆微服务”预先复制 DTO、Repository、事件总线或配置层；先在模块内部形成真实 seam。
- 不为演示新增默认种子。Demo 数据放在 Demo profile 的 fixture 中，并与生产数据库迁移分开。
- 不把已有测试数量当作质量目标。新增测试优先覆盖事务边界、资源归属、恢复、幂等、真实 MySQL、浏览器和目标环境。
- 每个阶段都要有“删除/合并清单”，阶段完成时清理至少一组已经被证明无价值的旧路径。

## 5. AOCI-CODE 与长上下文认知治理

aoci-code 的价值不在于给仓库再加一份摘要，而在于让 AI Agent 在行动前获得与代码版本绑定、可验证、可增量维护的系统认知。当前仓库仍缺少受治理的 Scope、Baseline、Receipt、漂移检查和恢复语义，因此下一阶段应采用 AOCI-CODE 的正式工作流，同时保留人类可读的架构文档。

参考实现：[aoci-spec/aoci-code](https://github.com/aoci-spec/aoci-code)。本次复核已阅读其 README、Cognition Volumes、Cognition Refresh 和公开协议；它强调 Whole-Index、Managed Scope、Evidence/Receipt、Baseline、漂移检测和上下文压缩后的重新认证，这些概念被转化为下面的 AgentHub 工程要求。

### 5.1 接入原则

1. 在稳定的绝对路径安装经过校验的 AOCI-CODE Release；若必须源码构建，记录 Git commit、构建命令和校验结果。安装工具不得读取业务数据、上传源码或保存 Provider 凭据。
2. 对 AgentHub 执行 `init` 和 `scan`，建立 Code Cognition；先不把 `target`、`node_modules`、生成证据、日志、密钥和临时工作区纳入 Managed Scope。
3. 对 Flyway 迁移和 JPA/Repository 结构启用只读 Database Cognition。只读取 Schema Evidence，不读取业务行数据，不将数据库密码写入索引。
4. 为八个高价值深模块建立 FRAS 语义条目：Identity、Project/Workspace、Workflow、Execution、Provider/Runtime、Conversation/Team、Artifact/Review、Sandbox/Deployment，以及前端 API 契约。
5. 以 Git 版本化正式认知资产；`.aoci` 中的 Ledger、草稿和 Recovery 状态按工具规则处理。AOCI 索引是 Agent 的认知入口，不是源码、测试和运行证据的替代品。
6. 在阶段收尾执行 `verify`/`check`，要求认知回到 `aligned`。发生大规模重构、数据库迁移或已知上下文压缩时，先重新读取 Rules、Whole-Index 和当前任务证据，再继续工作。
7. 在 CI 中先使用 AOCI 官方可执行文件做确定性校验；若组织暂时不能在 CI 安装该工具，则至少校验索引指纹、受管范围、数据库迁移版本和源码变更清单，禁止手工改 Baseline 掩盖漂移。

### 5.2 不把 AOCI 用成新的复杂度来源

- 不复制 AOCI 内部状态机到脚本或 Java 业务代码，只使用其 CLI/MCP 合同。
- 不为每个类写长篇条目；优先维护模块职责、强关系、公开接口、事务边界、不可破坏约束和验证入口。
- 不让模型生成的关系覆盖精确调用图、源码和测试事实；任务执行仍必须回到源码、数据库和运行结果核对。
- 认知条目过期时标记 `stale` 或 `repair_required`，不能继续以“完整理解”口径修改高风险模块。

## 6. 分阶段长期路线

各阶段按顺序推进，预计总周期 18～24 个月。时间是排期参考，退出条件是完成标准。每阶段都必须完成：现状基线 → 方案/ADR → 实现 → 自动化验证 → 目标环境验证 → 文档/认知对齐 → 删除旧路径 → 阶段验收。

### Phase 0：产品边界、事实基线与瘦身冻结（3～4 周）

**目标**：在继续写功能前固定产品主线、认知资产和代码收敛规则，停止“为了展示而扩展”。

**必须完成**：

1. 写出 Product Brief：目标用户、首个项目模板、核心旅程、明确不做的能力、三种运行模式、数据保留和安全边界。
2. 建立资源和事件词汇表：Project、Workspace、WorkflowDefinition、Run、StepRun、Event、Artifact、Approval、Deployment、Agent Policy、Context Source；发现代码与文档冲突时以实际持久化模型和 API 契约为准。
3. 重新生成 API、数据库表、前端调用、Provider/Runtime/Sandbox 能力矩阵；每一项标明 owner、来源、认证、幂等、状态、测试和删除日期。
4. 接入 AOCI-CODE，完成 Code/Database Cognition 初始化、Managed Scope、首个 Baseline 和 `aligned` 验证；把本次删除的 `ARCHITECTURE_INDEX.md` 内容仅作为迁移参考，不能重新恢复为无治理的唯一索引。
5. 建立瘦身清单：旧包、旧 Controller、默认种子、兼容 DTO、重复状态机、未使用 Provider、过大前端文件。每项写明调用证据、替代路径、迁移窗口和删除负责人。
6. 把 `CHANGELOG.md`、白皮书、能力边界、E2E 指南和 README 中的“全部完成”“生产级”“真实全链路”等表述改成有证据的当前状态；每个数字注明采集命令、环境和时间。
7. 增加禁止新债务的自动检查：生产代码不得新增固定 user/project/path，前端不得新增 localhost 直连，生产 API 不得新增 Mock fallback，旧路径不得出现新调用。

**禁止事项**：不新增 Provider、不新增聊天功能、不拆微服务、不做视觉重构、不把 AOCI 索引当作业务数据库。

**退出证据**：Product Brief、词汇表、API/Schema/能力矩阵、AOCI Receipt 和 Baseline、瘦身清单、更新后的能力声明均进入同一阶段交付；CI 能检测到默认值、旧路径和索引漂移；Phase 0 结束后冻结新增范围。

### Phase 1：模块收敛与 API/数据契约 v1（5～7 周）

**目标**：让调用者面对少量、稳定、可版本化的深模块接口，消除旧新双轨行为。

**后端**：

- 选定 `identity`、`project`、`orchestration`、`execution`、`agent`、`audit`、`sandbox`、`ops` 八个生产上下文；原型 `domain.*` 只作为迁移源，不再作为新的业务入口。
- 为每个上下文定义 Command、Query、Event 和错误码；Controller 不直接编排线程、JPA Entity 或文件路径。
- 将 `ExecutionApplicationService` 拆成 Run Command、Run Query、Run Evidence、Event Stream 四个内部深模块；将 `DagExecutionEngine` 拆成拓扑计划、租约执行、状态转换、Agent 调用、事件记录五个内部 seam，但对外保留一个 Run 执行接口。
- 统一 Workspace、Workflow、Run、Artifact 和 Deployment 的资源归属查询；移除隐式 `user-1`、`proj-default`、`run-default` 和 `system` 业务默认值，Demo 种子迁移到独立 profile。
- 只保留一个工作区 REST 资源模型（`/api/workspaces/{workspaceId}` 族）；为旧接口提供短期 410/迁移信息和调用计数，然后删除。
- 固化分页、排序、错误响应、幂等键、ETag/版本号、SSE 游标和时区规则，生成 OpenAPI 或等价契约快照。

**前端**：

- 将 845 行 API 文件（`services/api.ts`）拆成 auth、projects、workspaces、workflows、runs、approvals、artifacts、deployments、stream 和 errors 模块；统一请求、重试、取消、401/403 和空状态。
- 将 677 行主页面（`app/page.tsx`）拆为项目壳、Run 工作区、审查侧栏和部署面板；页面只依赖资源查询和命令接口。
- 删除 `WorkflowCanvas.tsx` 的 localhost 直连、默认 ID 和兼容请求；所有资源通过当前 Project/Workspace/Run 上下文注入。

**退出证据**：生产 profile 无旧 Controller 注册；旧接口调用计数为零后完成删除；同一业务状态不存在两个 Repository/状态机；OpenAPI 与前端契约测试一致；ArchUnit/静态检查阻止跨层直连；Phase 1 至少删除或合并一组重复实现。

### Phase 2：项目接入与受控工作区产品化（6～8 周）

**目标**：用户可以把一个真实代码项目安全接入 AgentHub，并在隔离分支/工作区中开始一次可审查变更。

**必须完成**：

1. 首先支持一个清晰的 Git 来源范围：本地受控目录和一个远程 Git Provider（优先 GitHub HTTPS/只读 Token）。凭据只保存 secretRef，clone/fetch 日志全程脱敏。
2. 增加 Project Onboarding 向导：连接来源、验证权限、选择默认分支、创建 Workspace、扫描语言/构建命令、生成项目规则、创建初始基线；失败步骤可重试或清理。
3. Workspace 只接受内部 `workspaceId` 和相对路径；所有 clone、branch、snapshot、diff、revert、cleanup 都由服务端策略执行。明确符号链接、子模块、大文件和 `.git` 目录策略。
4. 建立工作区容量、文件数、单文件大小、执行时长、网络访问和并发锁配额；超限进入可解释的 `BLOCKED_POLICY`，不能静默截断。
5. 将 AOCI Code/Database Cognition、项目规则、构建文件、测试约定作为版本化 Context Source，显示来源、commit、更新时间和安全级别。
6. 提供“重新同步、基线变更、放弃工作区、恢复上一次快照”操作；每项都有审计记录和确认门槛。

**禁止事项**：不允许用户提交宿主绝对路径、不把 GitHub Token 写入数据库正文、不默认执行未知仓库的任意构建命令、不把扫描结果直接当作 Agent 指令。

**退出证据**：在真实 Git 仓库上完成接入、同步、创建隔离分支、Agent 修改、JGit Diff、回滚和清理；两个用户不能看见彼此的仓库文件；故意断网、权限失效、超限和路径穿越均有可恢复错误；AOCI/项目规则与 commit 指纹绑定。

### Phase 3：Agent 工作单元、模板和策略产品化（8～10 周）

**目标**：把“适配器集合”变成可复用、可评估、可限权的 Agent 工作单元。

**必须完成**：

- 定义版本化 `AgentDefinition`：角色目标、系统提示、输入输出 schema、工具清单、Provider 策略、上下文来源、预算、超时、重试、停止条件和安全等级。
- 定义版本化 `WorkflowTemplate`：需求澄清、计划、实现、测试、审查、审批和交付节点；模板引用 AgentDefinition 快照，不直接引用可变 Provider 配置。
- Orchestrator 先生成结构化 Plan，再由用户确认范围、文件、工具和预算；未经确认不能执行高影响工具。
- 工具以 allowlist 和参数 schema 暴露，文件写入、命令执行、网络请求、Git 操作和部署分别授权；工具输出必须带来源和大小上限。
- 设计 Context Assembly：项目规则、AOCI 条目、相关文件、历史 Run 摘要、工具结果和用户输入分层装配；每层记录 token 预算、截断方式和安全等级。
- Provider 路由只在能力、成本、延迟、区域和数据策略满足时选择；真实失败要进入失败或明确降级状态，Mock 不得隐式成为产品成功。
- 建立离线评测集：至少包含新增接口、修复缺陷、重构、测试补齐和恶意 Prompt 五类任务；评测通过率、工具误用率、Diff 可接受率、Token 成本和人工修改量。

**首批模板建议**：`修复一个带测试的后端缺陷`、`为现有 Spring Boot 服务新增一个小接口并更新测试`。多 Agent 角色只服务于这两个模板，不为展示而扩展角色数量。

**退出证据**：两个模板在 Demo 和 Live Provider 模式均能运行；所有 Agent 工具调用可审计、可取消、可重放；同一评测集有基线模型和版本化结果；用户能在执行前看到计划、权限和预算；失败不会被模拟输出掩盖。

### Phase 4：Run Operations、人工决策与长上下文可靠性（8～10 周）

**目标**：把一次 Run 做成可观察、可暂停、可恢复、可审查的产品对象，而不是后台线程的外显。

**必须完成**：

1. 将 Run 状态、Step 状态、租约、事件、Outbox、Approval 和 Artifact 形成单一状态图；所有转换通过状态机和条件更新完成。
2. 完善 at-least-once 执行：幂等键、attempt、外部调用 requestId、工具副作用记录、重试分类、超时、取消和人工接管；把“可自动重试”和“必须人工确认”分开。
3. 完成 transactional outbox 或同等机制，保证数据库状态、SSE 回放、通知和审计不会互相失配；多实例广播只有在负载证据表明需要时才引入 Redis Stream。
4. 让用户能查看计划、上下文来源、工具调用、成本、风险、失败原因、重试次数和待决策项；任何暂停都能说明下一步需要谁做什么。
5. 将长上下文拆为项目级、Run 级和 Step 级记忆。摘要必须引用原始事件范围和版本；超过预算时优先压缩低价值日志，不能丢失约束、审批、错误和文件来源。
6. 已知上下文压缩或换 Agent 后，先重新读取 AOCI Whole-Index、当前 Run 快照和未完成操作 Receipt；摘要只能帮助定位，不能证明系统认知仍然可靠。
7. 增加浏览器真实操作：刷新、断线、重复点击、并行打开两个 Run、审批后恢复、取消后重试；页面始终从 API 恢复事实。

**退出证据**：强杀/重启、数据库连接短暂中断、SSE 断线、Provider 超时、重复提交、审批等待和取消场景都能得到可验证终态；已成功 Step 不重复产生外部副作用；长上下文任务的来源、预算、摘要和恢复流程可在证据包中核查。

### Phase 5：质量门禁、审查和交付产品化（8～10 周）

**目标**：让 AgentHub 交付的是“可接受变更”，而不是“生成了一段代码”。

**必须完成**：

- 把 Diff、测试、静态分析、依赖/秘密扫描、构建产物、人工意见和 Approval 统一为 Artifact Lineage；每个 Artifact 绑定 Run、Workspace commit、工具版本和校验和。
- 建立质量门禁策略：后端 Maven、前端 Next、格式/静态检查、安全扫描和最小回归必须按项目模板配置；失败门禁不能靠前端按钮伪造通过。
- 审查页面支持按文件、风险、Agent、工具和测试结果过滤；用户可以请求某个 Step 重做、回滚单个文件或退回整个 Run。
- 预览部署只允许已接受 Artifact 和受控模板；明确静态 Web 与一个服务模板的 BUILD/START/HEALTH_CHECK/RUNNING/STOP/CLEANUP 证据，记录资源、端口、日志游标、TTL 和清理结果。
- 可选接入一个远程 Git Provider 的 Pull Request 草稿/只读审查能力；外部写操作必须有明确用户授权、幂等键和审计回执。
- 证据包按用户、项目、Run 和保留策略导出，支持离线审计；脱敏、checksum、版本和模式标识必须保持稳定。

**退出证据**：真实仓库变更必须经过质量门禁和用户批准才能进入预览；部署 URL 能从另一客户端访问并通过健康探针；停止/TTL/失败后端口、进程、容器和工作区临时文件全部回收；回滚结果可访问且证据链完整。

### Phase 6：组织、权限、预算与可运营性（8～10 周）

**目标**：让多个研发团队在同一个 AgentHub 实例中安全、可控地长期使用。

**必须完成**：

1. 引入 Organization/Member/Role/Project Membership，明确 Owner、Admin、Developer、Reviewer、Viewer、Auditor 权限；角色矩阵覆盖 Project、Provider Secret、Workflow、Run、Artifact、Deployment、Evidence 和运维端点。
2. 将当前应用层 owner isolation 演进为组织和项目级授权；是否需要 Schema-per-Tenant 或 Database-per-Tenant 必须由数据量、法规和威胁模型决定，不能为了“看起来企业级”提前实现。
3. Provider 凭据采用 secretRef、轮换、最小权限、访问审计和删除后不可恢复策略；用户只能看到脱敏摘要。
4. 实现项目/组织级 Token、并发 Run、工作区、上下文、Provider 费用和部署时长预算；预算耗尽时停止新副作用，并保留可解释的终止原因。
5. 完善观测：Run/Step/Provider/工具/部署关联 traceId，日志结构化脱敏，指标带项目和模式标签；建立 SLO、告警、值班手册和用户可见状态页。
6. 提供数据导出、删除、保留期、备份还原和审计查询；备份演练必须在干净环境验证，而不是只验证压缩包存在。

**退出证据**：两个组织和多个角色的越权、预算、密钥、证据导出和审计场景全部通过；运维人员可以定位一次失败 Run 的完整链路；备份还原、数据删除和权限变更有演练记录。

### Phase 7：可扩展运行平面与受控发布（10～14 周）

**目标**：只有在 Phase 6 的单节点产品已有真实使用负载后，才引入必要的水平扩展和更强隔离。

**触发条件**：连续四周观测到单节点 CPU/内存/队列/数据库或 SSE 连接成为实际瓶颈，或者出现明确的租户隔离需求。没有触发条件时继续保持模块化单体和数据库驱动 Worker。

**可能工作**：

- 把 Agent 执行、沙箱部署、事件广播和 Web/API 进程拆成最少的 Worker 角色；每次拆分都先定义消息、幂等、重试、租约和回滚契约。
- 需要跨节点实时广播时引入 Redis Stream 或同等方案；数据库仍是 Run/Step/Event 的权威来源，消息系统不能成为第二套状态机。
- 工作区和 Artifact 迁移到 S3/MinIO 等对象存储时，先完成 checksum、版本、生命周期、权限和迁移回滚；不直接把本地路径替换成一个未经验证的 URL。
- 对公网不可信代码执行有真实需求时，再评估独立 Sandbox Worker、网络隔离、只读镜像、资源配额、Kubernetes、gVisor/Kata/Firecracker 等方案；在此之前明确产品只适用于受控团队。
- 用真实负载测试确定数据库索引、连接池、队列、SSE 回放和 Provider 限流参数；不引用本地 50 线程测试作为集群 SLO。

**退出证据**：多实例部署下 Run/Step 状态、事件序列、审批、取消和证据包一致；Worker 故障可以接管且不重复外部副作用；对象存储、网络和沙箱故障有明确恢复策略；发布可灰度、可回滚并有版本兼容矩阵。

### Phase 8：稳定版发布与生态边界（持续 8～12 周后进入维护）

**目标**：形成可以被团队长期安装、升级、审计和扩展的产品发行版。

**必须完成**：

- 发布稳定 API/CLI/配置版本，提供从当前 v2.x 到稳定版的迁移工具和回滚说明。
- 提供一个最小 SDK 或 Webhook 合同，只暴露已稳定的 Project、Run、Artifact、Approval 和 Deployment 能力；不把内部 Entity 直接变成公共接口。
- 建立 Provider、AgentDefinition、WorkflowTemplate、Tool 和 Deployment Template 的扩展合同；第三方扩展必须有权限、版本、测试和撤销机制。
- 提供安装包、Compose、升级、备份、卸载、数据导出和故障诊断文档；所有文档从干净环境重新验证。
- 以真实用户旅程、脱敏证据包、测试报告、负载报告和已知边界作为版本发布物；简历只使用这些证据中的指标。

**暂缓的生态方向**：公开 Agent 市场、按量计费、移动端、跨公司公共执行平台和任意代码云沙箱。它们只有在稳定版有明确用户需求、威胁模型和运营能力后才另立计划。

## 7. 每阶段固定的工程交付门禁

以下门禁对 Phase 0～8 都适用：

1. **需求门禁**：阶段开始前写清用户动作、后端事实、权限、失败状态、成本、可观测字段和删除对象；不接受只有 UI 截图的需求。
2. **架构门禁**：涉及模块边界、状态机、数据库、公共接口或数据保留的变更，先写 ADR，并更新词汇表、AOCI 条目和影响矩阵。
3. **实现门禁**：Controller 不直接持久化/调度；前端不写业务状态；Provider、工具、沙箱和部署均通过明确 seam；旧路径不能产生新数据。
4. **测试门禁**：单元测试只证明局部逻辑；真实 MySQL/Flyway 证明 Schema 和事务；黑盒 API 证明权限和契约；浏览器证明用户旅程；目标环境证明容器、网络、Provider 和部署行为。报告中分别标记四类证据。
5. **认知门禁**：AOCI `verify/check` 对齐；发生已知上下文压缩时重新读取系统认知和任务快照；索引漂移、缺少 Receipt 或未接受 Evidence 时停止高风险修改。
6. **Git/CI 门禁**：每个阶段至多一个普通提交和一个加固提交；阶段代码、测试、迁移、配置和计划验收记录必须在同一次提交中完成；提交前执行后端 `mvn clean test`、前端 `npm run build` 以及涉及的 Compose/镜像/真实 MySQL 门禁，并按项目要求同步到 `origin/main`。
7. **发布门禁**：发布前重新读取改动和 diff，生成变更清单、回滚方式、已验证环境和未验证限制；没有目标环境证据时只能称为“代码/自动化测试完成”。

## 8. 长期风险与停止规则

| 风险 | 触发信号 | 处理 | 停止扩展的条件 |
| --- | --- | --- | --- |
| AI 生成错误或越权修改 | 高风险文件命中、工具参数异常、审查退回率上升 | 计划确认、工具 allowlist、质量门禁、人工审批、回滚 | 不能说明文件来源和影响时禁止执行 |
| 重试导致重复花费或重复写入 | Provider 账单重复、同一 Step 多副作用 | 幂等键、attempt、外部 requestId、补偿 | 无法证明幂等时禁止自动重试 |
| 上下文漂移 | AOCI stale、摘要缺原文范围、换 Agent 后结论冲突 | 刷新 Whole-Index、补 Evidence、重新确认 | 认知未 aligned 时禁止高影响变更 |
| 代码继续膨胀 | 新增平行接口、适配器无使用者、大类继续变长 | 删除预算、模块深度评审、冻结新功能 | 未完成 Phase 0/1 的瘦身不得扩展生态 |
| 沙箱越界或敏感信息泄露 | 路径/命令/网络探针异常、日志出现密钥 | 默认拒绝、环境清洗、隔离 Worker、审计和轮换 | 未达到威胁模型要求时禁止公网不可信执行 |
| Provider 依赖和成本失控 | P95 延迟、429、成本预算超限 | 路由策略、熔断、预算、人工暂停 | 没有成本和降级证据时不增加 Provider |
| 运维不可恢复 | 备份不能还原、事件与状态不一致、Worker 无法接管 | 故障演练、Outbox、租约、版本兼容 | 恢复演练失败时不做发布或扩容 |

## 9. 阶段验收记录模板

每个阶段完成时，在本文件对应阶段末尾或单独的验收记录中填写：

```text
阶段：Phase X
范围：
代码完成：是/否，变更提交：
自动化测试：后端 __ tests；前端 build；MySQL/Flyway；浏览器/E2E；镜像/Compose
目标环境验证：环境、日期、运行方式、结果
AOCI：Baseline/Receipt、verify/check 结果
删除或合并的旧路径：
未验证项：
已知风险与回滚方式：
普通提交 Subject：
加固提交 Subject（如有）：
```

没有目标环境证据的阶段，必须明确写“目标环境验证：未完成”，不能用 CI 绿灯替代。没有完成退出条件的阶段不得进入下一阶段，也不得通过新增功能来规避未解决的收敛、瘦身、安全和恢复问题。

---

### 9.1 Phase 0 Product Brief 正式基线

| 维度 | 正式基线定义 |
| --- | --- |
| **目标用户** | 3～10 人、已有 Git 仓库与代码评审习惯、需要交付内部系统或业务服务的研发团队。 |
| **首个项目模板** | 受控 Spring Boot 3.x / Next.js 代码仓库变更模板。首批支持两个研发变更模板：`修复一个带测试的后端缺陷`、`为现有 Spring Boot 服务新增一个小接口并更新测试`；受控预览模板支持 `STATIC_WEB_TEMPLATE` 与 `JAVA_SPRING_TEMPLATE`。 |
| **核心用户旅程** | `登录鉴权 -> 接入 Project -> 创建受控 Workspace -> 选择 Workflow Template -> Orchestrator 生成并解释计划 -> 用户确认 -> Agent 在受控工作区执行 -> Diff/测试/风险审查 -> Approval 审批决定 -> 受控预览或交付物 -> 导出脱敏 Run Evidence Bundle`。 |
| **明确不做的能力** | 不承诺公网不可信代码执行、任意 Shell 平台、通用 Agent 市场、物理分库多租户（Schema/DB-per-Tenant）、移动端和无限制 Provider 矩阵；IM/群聊仅作为协作扩展，不作为产品主线。 |
| **三种运行模式** | `DEMO_SIMULATED`（无密钥体验、CI 确定性回归，强制标记 `simulated=true`）、`LIVE_SELF_HOSTED`（个人或小团队自托管，已配置真实 Provider + 受控模板，默认单节点）、`LIVE_TEAM`（受控研发团队长期使用，需 Phase 6 组织/角色/预算/审计/备份/SLO 完成后启用）。 |
| **数据保留策略** | 1) `workflow_runs`、`step_runs`、`run_events`、`approvals`、`artifacts`、`token_usages`、`auth_audit_logs` 默认持久化于 MySQL 8.0 数据卷；<br>2) 工作区目录仅在关联项目 `projects.status = 'ARCHIVED'`、无活跃 `WorkflowRun` 且无活跃 `workspace_locks` 租约时，经 `DisasterRecoveryBackupService.cleanupArchivedWorkspacesWithDbReconciliation` 对账清理；<br>3) 冷备归档（`backup-prod.sh` / `backup-prod.ps1`）含 `SHA256SUMS` 与可选 AES-256 加密，默认保留 14 天；<br>4) `invalidated_tokens` 保留至 `expires_at` 过期；SSE `stream-ticket` 内存 TTL 60 秒且单次消费即焚。 |
| **安全边界** | 1) 生产环境（`prod` profile）强制外部注入 `AGENTHUB_AUTH_SECRET`（>=32 字符）和 `ACTUATOR_PROMETHEUS_TOKEN`，禁用 `dev-token-*`、`X-User-Id` 伪造头、`user-1`/`system` 超级用户旁路及 `/api/workspace/*` 旧接口；<br>2) LLM 凭据仅存 `secretRef`（如 `env:DEEPSEEK_API_KEY`），经 `SecretMasker` 全链路脱敏；<br>3) Workspace 仅接受服务端 `workspaceId` 与受检相对路径，拦截绝对路径、`../`、符号链接逃逸、保留设备名、NTFS 8.3 短名及 `.git` 直接写入（单文件写上限 10MB / 预览 2MB）；<br>4) 沙箱部署仅允许 `ACCEPTED` 产物与白名单命令，容器以非 root 运行（backend `10001`、frontend `1001`、sandbox `1000:1000`）。 |

### 9.2 Phase 0 瘦身与收敛清单 (Slimming & Convergence Inventory)

> 资源和事件词汇表、数据库表矩阵见 [`docs/ARCHITECTURE.md`](ARCHITECTURE.md)；API 与前端调用矩阵见 [`docs/API.md`](API.md)；Provider/Runtime/Sandbox 能力矩阵见 [`docs/CURRENT_CAPABILITY_BOUNDARIES.md`](CURRENT_CAPABILITY_BOUNDARIES.md)。

| 类别 | 目标对象（精确路径 / 符号） | 当前调用证据 | 替代路径 | 迁移窗口 | 负责人 | 当前状态 |
| --- | --- | --- | --- | --- | --- | --- |
| **旧认知与旧计划文档** | `ARCHITECTURE_INDEX.md`、`docs/AGENTHUB_LONG_TERM_PLAN.md`、`docs/AGENTHUB_NEXT_LONG_TERM_PLAN.md` | 全仓 `grep` 引用已清零 | `docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md`、`docs/ARCHITECTURE.md` 及 AOCI 资产 | **Phase 0（已删除）** | Architecture Owner | **已删除**（由 `Phase0BaselineAndDebtFreezeGuardTest` 防复活） |
| **前端 localhost 与裸路径直连** | `agenthub-frontend/src/components/WorkflowCanvas.tsx` (`http://localhost:8080/api/workspace/workflow/execute`、`d:/work/agenthub/...`)、`services/api.ts` (`MOCK_DEPLOYMENT.url`) | `WorkflowCanvas.tsx` 无外部页面引用；`api.ts` 已改为 `DEFAULT_PREVIEW_URL` | `apiClient.startRun` (`/api/executions/runs`) 与相对路径 `API_BASE` | **Phase 0（已清理）** | Frontend Owner | **已清理**（前端源码 `localhost:8080` 与 `/api/workspace/` 清零） |
| **旧包 (`domain.*` / `application.*` / `adapter.web.*`)** | `com.agenthub.domain.*`、`com.agenthub.application.service.IMCollaborationService`、`com.agenthub.adapter.web.*` | `SystemHealthController` 已迁入 `com.agenthub.ops.api`，`AgentRegistryController` 已迁入 `com.agenthub.agent.api`；重复 `WorkspaceLockManager` 与 `ErrorCode` 已物理删除；其余旧包零新增调用 | 八大生产上下文（`identity`、`project`、`orchestration`、`execution`、`agent`、`audit`、`sandbox`、`ops`） | **Phase 1（已收敛）** | Backend Owner | **已收敛**（由 `ArchUnitArchitectureTest` 与 `Phase1ModuleConvergenceAndContractTest` 守护） |
| **旧 Controller 与重复路由** | `WorkflowAndDiffController` (`/api/workspace/*`)、`IMController` (`/api/im/conversations*`)、`RunAliasController` (`/api/runs*`)、`SandboxController` (`/api/sandbox/*`) | 前端对 `/api/workspace/*`、`/api/im/*`、`/api/runs*`、`/api/sandbox/*` 调用已全部清零；四个旧 Controller 均标记 `@Deprecated` + `@Profile("!prod")` + `LegacyEndpointMigrationFilter` (RFC 8594 `Deprecation`/`Sunset`/`Link` 头与 `agenthub_legacy_endpoint_calls_total` 指标) | `WorkspaceController` (`/api/workspaces/{workspaceId}/*`)、`ConversationController` (`/api/conversations*`)、`ExecutionController` (`/api/executions/runs*`)、`DeploymentController` (`/api/deployments*`) | **Phase 1（已阻断/下线）** | Backend & Frontend Owner | **已完成**（`prod` 环境全部禁用，前端零调用，由 `Phase1ModuleConvergenceAndContractTest` 验证） |
| **默认种子与隐式默认值** | Flyway `V2`/`V6`/`V7` 中的历史种子；13 个后端 Java 类中的 `"user-1"` / `"proj-default"` / `"run-default"` 兜底常量与 3 个前端文件中的 `"proj-default"` | 前后端生产源码中 `"user-1"`、`"proj-default"`、`"run-default"` 兜底已 100% 清零；`agenthub.demo.seed-enabled` 默认 `false`，仅在 `application-demo.yml` (`demo` profile) 显式开启 | 显式从 `RequestContext`、Project/Workspace/Run 上下文提取，由 `ResourceOwnershipVerifier` 统一校验资源归属 | **Phase 1（已清零）** | Backend & Frontend Owner | **已清零**（`Phase0BaselineAndDebtFreezeGuardTest` 冻结上限已收紧为 `Map.of()` 零容忍） |
| **兼容 DTO 与重复模型** | `com.agenthub.domain.workflow.model.WorkflowModels` vs `com.agenthub.orchestration.domain.dsl.WorkflowDsl`；重复 `ErrorCode` 与 `WorkspaceLockManager` | `infrastructure.common.exception.ErrorCode` 与 `infrastructure.jgit.WorkspaceLockManager` 已物理删除；`WorkspaceController` 已彻底解耦 `WorkflowEngineService` 与底层 `Path`/`JGitWorkspaceManager` | 统一使用 `com.agenthub.orchestration.domain.dsl.WorkflowDsl`、`com.agenthub.shared.exception.ErrorCode` 与 `com.agenthub.infrastructure.concurrency.WorkspaceLockManager` | **Phase 1（已收敛）** | Backend Owner | **已收敛**（重复类已物理删除，Controller 层零底层实现依赖） |
| **重复状态机与调度器** | `WorkflowEngineService`（阶段 1 内存原型）、`ExecutionScheduler`（阶段 3 线性调度器）vs `DagExecutionEngine`（生产内核） | 两者均已标记 `@Deprecated` 且由 ArchUnit 禁止任何新调用；生产 Run 执行唯一由 `DagExecutionEngine` 及其 5 个内部模块承载 | 唯一生产执行内核 `DagExecutionEngine` | **Phase 1（已隔离）** | Execution Owner | **已隔离**（由 `ArchUnitArchitectureTest` 冻结禁止新增调用） |
| **未使用或原型 Provider/Adapter** | `com.agenthub.infrastructure.adapter.*`（`CliProcessAdapter`、`CodexCliAdapter`、`OpenClawCliAdapter`、`SpringAiApiAdapter`）及 `AgentAdapterFactory` / `UnifiedAgentAdapter` | 主链路 DAG 使用 `AgentRuntimeRegistry` + `LlmProvider`；原型 Adapter 已由 ArchUnit 冻结禁止新依赖 | `ProviderDrivenAgentRuntime` + `LlmProvider` SPI | **Phase 1（已隔离）** | Agent Runtime Owner | **已隔离**（由 `ArchUnitArchitectureTest` 冻结禁止新增调用） |
| **过大后端与前端模块** | 后端：`ExecutionApplicationService` (947->151 行)、`DagExecutionEngine` (918->452 行)；<br>前端：`services/api.ts` (845->98 行)、`app/page.tsx` (677->402 行) | `ExecutionApplicationService` 拆分为 `RunCommandModule`/`RunQueryModule`/`RunEventStreamModule`/`RunEvidenceModule`；`DagExecutionEngine` 拆分为 `DagTopologyPlanner`/`DagLeaseExecutor`/`DagStateTransitioner`/`DagAgentInvoker`/`DagEventRecorder`；前端 `api.ts` 拆分为 `services/api/*` 10 个模块，`page.tsx` 拆分为 4 个工作台壳组件 | 后端深模块门面 + 内部职责模块；前端按资源模块与工作台壳组件拆分 | **Phase 1（已完成）** | Full-Stack Owner | **已完成**（由 `Phase1ModuleConvergenceAndContractTest` 强制校验行数上限与模块结构） |

### 9.3 Phase 0 AOCI-CODE 认知治理基线与阻塞记录

- **Managed Scope（受管范围定义）**：
  - 包含：`agenthub-backend/src/main/java`、`agenthub-backend/src/main/resources/db/migration`（`V1`～`V12`）、`agenthub-frontend/src`、`docs/`、`README.md`。
  - 排除：`**/target/**`、`**/node_modules/**`、`/**/.next/**`、`data/workspaces/**`、`data/backups/**`、`.env*`、凭据与临时日志。
- **八个高价值深模块 FRAS 规划范围**：
  1. `identity`（认证、令牌吊销、流式票据、`ResourceAccessGuard`）
  2. `project`（`Project`、`Workspace`、`DefaultWorkspaceResolver`、`WorkspaceLockManager`）
  3. `orchestration`（`WorkflowDefinition`、Kahn 拓扑校验、`SafeExpressionEvaluator`、`Approval`）
  4. `execution`（`WorkflowRun`、`StepRun`、`RunEvent`、`DagExecutionEngine`、`PersistentExecutionRecoveryRunner`）
  5. `agent` / `runtime`（`LlmProvider`、`DynamicProviderRouter`、`CircuitBreaker`、`SecretMasker`、`TokenUsage`）
  6. `conversation` / `team`（`Team`、`Conversation`、`Message`、分段锁单调序号、`LoopDetector`）
  7. `audit`（`Artifact`、`JGitWorkspaceManager` 结构化 Diff 与非破坏性 Revert、`RunEvidenceBundleView`）
  8. `sandbox` / `ops`（`DeploymentLifecycleWorker`、`LocalProcessSandbox`、`DockerSandbox`、`HealthCheckProbeService`、`DisasterRecoveryBackupService`）及前端 API 契约（`services/api.ts`）
- **当前环境阻塞与合规处理（遵循 `AGENTS.md` §4 与本文 §5.1.7）**：
  - 经在当前工作环境执行 `Get-Command aoci*, *aoci*` 与 `where.exe aoci aoci-code` 核查，本地环境与 CI Runner 尚未安装 `aoci-code` 官方可执行文件或 MCP 服务。
  - 严格遵守 `AGENTS.md` §4 铁律（“不能复制 AOCI 内部状态机，也不能伪造 AOCI 结果。AOCI 不可用时如实记录阻塞，继续执行不依赖 AOCI 的低风险工作”），本阶段**绝不手工伪造 `.aoci` 目录、Ledger、Receipt 或 `aligned` 输出**。
  - 同时按本文 §5.1.7 要求，在 `Phase0BaselineAndDebtFreezeGuardTest` 与 `.github/workflows/ci.yml` 中落地了确定性基线校验门禁：自动校验受管目录结构、Flyway `V1`～`V12` 迁移脚本集合与固定 SHA-256 指纹基线、22 张数据库表 Schema 对齐、18 个 `@RestController` 全景登记、废弃索引/旧计划文件零残留、前端零 `localhost` 直连及前后端默认 ID 冻结白名单。

### 9.4 Phase 0 阶段验收记录

```text
阶段：Phase 0
范围：产品边界（Product Brief）、核心资源与事件词汇表、API/数据库表/前端调用/Provider-Runtime-Sandbox 能力矩阵、瘦身与收敛清单、文档事实校准、前端 localhost/裸路径与静默 Mock 回退清理、新债务自动阻断门禁（ArchUnit + JUnit + CI）
代码完成：是，变更包含 WorkflowCanvas.tsx、services/api.ts、ArchUnitArchitectureTest.java、Phase0BaselineAndDebtFreezeGuardTest.java、FullLifecycleEndToEndIntegrationTest.java、.github/workflows/ci.yml 及全套治理文档
自动化测试：后端 53 test classes / 310 tests 通过（mvn clean test）；前端 npx tsc --noEmit 与 npm run build 通过；Compose 配置校验（docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet）通过
目标环境验证：未完成（本地完成 H2 MySQL Mode 全量测试、前端生产构建与 Compose 语法校验；真实 Linux + MySQL 8.0 + Docker 镜像与灾备演练由 GitHub Actions CI 流水线执行）
AOCI：阻塞（当前环境未安装官方 aoci-code CLI/MCP 可执行文件；按 AGENTS.md 规范不伪造 AOCI Receipt/Baseline，已通过 Phase0BaselineAndDebtFreezeGuardTest 锁定受管范围与 Flyway V1-V12 SHA-256 指纹基线）
删除或合并的旧路径：已删除 ARCHITECTURE_INDEX.md、docs/AGENTHUB_LONG_TERM_PLAN.md、docs/AGENTHUB_NEXT_LONG_TERM_PLAN.md；已清除 WorkflowCanvas.tsx 对 http://localhost:8080/api/workspace/workflow/execute、d:/work/agenthub/data/workspaces/default 的硬编码直连、非演示模式下的静默 Mock 回退与伪造完成提示，以及 services/api.ts 中的 localhost:8080 硬编码
未验证项：1) 官方 aoci-code CLI init/scan/verify/check 待环境安装官方二进制后补齐；2) 真实外部商业 LLM API Key 在生产多机环境下的长周期账单压测
已知风险与回滚方式：若后续需要回退 Phase 0 治理门禁或文档收敛，可通过 git revert 对应 Phase 0 提交恢复；所有业务数据库表结构（V1-V12）在 Phase 0 未发生破坏性变更，回滚零数据丢失风险
普通提交 Subject：feat(phase-0): establish product baseline, capability matrices, frontend path cleanup, and debt-freeze guards
加固提交 Subject（如有）：无
```

### 9.5 Phase 1 阶段验收记录

```text
阶段：Phase 1
范围：八大限界上下文收敛（identity/project/orchestration/execution/agent/audit/sandbox/ops）、跨上下文 Command/Query/DomainEvent 契约、深模块内部解耦（ExecutionApplicationService 拆分为 4 个子模块、DagExecutionEngine 拆分为 5 个子模块、WorkspaceController 下沉至 WorkspaceApplication）、API v1 契约规范（OpenAPI 3.0.3、X-API-Version/X-Correlation-Id/X-Execution-Mode、PageRequestQuery/PageResultView、RFC 8594 废弃响应头与观测指标）、全链路显式所有权校验（ResourceOwnershipVerifier，前后端 "user-1"/"proj-default"/"run-default" 生产兜底 100% 清零）、前端 API 与工作台页面模块化拆分
代码完成：是，变更包含 com.agenthub.shared.contract.*、com.agenthub.shared.filter.*、com.agenthub.ops.*、com.agenthub.execution.application.* (RunCommandModule/RunQueryModule/RunEventStreamModule/RunEvidenceModule)、com.agenthub.orchestration.scheduler.* (DagTopologyPlanner/DagLeaseExecutor/DagStateTransitioner/DagAgentInvoker/DagEventRecorder)、ResourceOwnershipVerifier、WorkspaceController/WorkspaceApplicationService、application-demo.yml、ArchUnitArchitectureTest（10 条规则）、Phase0BaselineAndDebtFreezeGuardTest（零容忍默认 ID）、Phase1ModuleConvergenceAndContractTest、前端 services/api/*（10 个子模块）与 components/dashboard/*（4 个壳组件）、docs/openapi-v1.json、docs/adr/ADR-006-context-convergence-and-api-contract-v1.md 及配套文档
自动化测试：后端 54 test classes / 317 tests 通过（mvn clean test）；前端 npx tsc --noEmit 与 npm run build 通过；Compose 配置校验（docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet）通过
目标环境验证：未完成（本地完成 H2 MySQL Mode 全量测试、前端生产构建与 Compose 语法校验；真实 Linux + MySQL 8.0 + Docker 镜像与灾备演练由 GitHub Actions CI 流水线执行）
AOCI：阻塞（当前环境未安装官方 aoci-code CLI/MCP 可执行文件；按 AGENTS.md 规范不伪造 AOCI Receipt/Baseline，已通过 Phase0BaselineAndDebtFreezeGuardTest 与 Phase1ModuleConvergenceAndContractTest 锁定受管范围、OpenAPI v1 契约与 Flyway V1-V12 SHA-256 指纹基线）
删除或合并的旧路径：已物理删除重复类 com.agenthub.infrastructure.jgit.WorkspaceLockManager 与 com.agenthub.infrastructure.common.exception.ErrorCode；已将 adapter.web.SystemHealthController 迁入 com.agenthub.ops.api.SystemHealthController、adapter.web.AgentRegistryController 迁入 com.agenthub.agent.api.AgentRegistryController；前端已彻底移除对 /api/im/*、/api/runs*、/api/sandbox/* 的兼容回退调用；四个旧兼容 Controller（WorkflowAndDiffController、IMController、RunAliasController、SandboxController）已全部标记 @Deprecated + @Profile("!prod") 并接入 LegacyEndpointMigrationFilter
未验证项：1) 官方 aoci-code CLI init/scan/verify/check 待环境安装官方二进制后补齐；2) 真实外部商业 LLM API Key 在多机环境下的长周期账单压测
已知风险与回滚方式：若后续需要回退 Phase 1 模块收敛或契约变更，可通过 git revert 对应 Phase 1 提交恢复；Flyway V1-V12 迁移脚本未做任何修改，数据库 Schema 100% 兼容，回滚零数据丢失风险
普通提交 Subject：feat(phase-1): converge bounded contexts, decompose deep modules, and establish API contract v1
加固提交 Subject（如有）：fix(phase-1): harden ownership verifier wiring, legacy telemetry metrics, openapi parity, and live team state
```
