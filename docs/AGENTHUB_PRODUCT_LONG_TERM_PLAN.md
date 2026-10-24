# AgentHub 产品化长期演进计划

> 版本：v4.0
> 基线日期：2026-10-16
> 规划跨度：24～36 个月；本文件是唯一长期计划。除非产品定位、部署边界或安全假设发生变化，不重新起草下一份计划，只在本文件末尾追加阶段证据。
> 当前状态：上一份计划的 Phase 0～Phase 8 已完成其代码和自动化验收记录；真实目标环境、AOCI 正式接入和可持续运营仍未完成。

本文定义 AgentHub 从“功能齐全的验证平台”到“可被小型研发团队长期使用的 AI 软件变更交付产品”的后续路径。它不是功能愿望清单。每个阶段都必须先收敛事实、再实施最小范围、最后用代码、测试、目标环境和运行证据证明结果。

## 1. 终点和产品选择

### 1.1 产品承诺

AgentHub 面向已有 Git 仓库和代码评审习惯的 3～10 人研发团队，承诺一条可审查的变更交付链路：

```text
接入项目 -> 选择变更模板 -> Orchestrator 生成计划 -> 用户确认
-> Agent 在受控 Workspace 执行 -> Diff/测试/风险门禁
-> 人工审批 -> 预览或外部交付 -> Run Evidence
```

产品的核心对象是 Project、Workspace、Workflow Template、Run、Step、Artifact、Approval、Deployment 和 Evidence。Conversation、Mention、Team Chat 只服务于这条链路，不把 AgentHub 继续扩展成通用 IM 或 Agent 社区。

首批长期支持的变更模板保持窄而稳定：

1. 为 Spring Boot 服务修复一个带回归测试的缺陷；
2. 为已有 Spring Boot 服务增加一个小型接口并更新测试；
3. 为已有 Next.js 页面完成一个受质量门禁约束的局部变更。

新模板必须有真实用户旅程、预算、工具权限、质量门禁、回滚方案、评测样本和删除条件；只有为了演示而增加的模板不得进入生产注册表。

### 1.2 运行模式

| 模式 | 允许的事实 | 启用条件 |
| --- | --- | --- |
| `DEMO_SIMULATED` | 确定性 Mock、离线工作区、模拟 Provider 和模拟部署 | 可用于 CI 和离线演示；所有结果必须带模拟标识 |
| `LIVE_SELF_HOSTED` | 真实 Provider、单节点受控执行、真实 Git 和预览 | 目标环境验证通过，不能静默回退到 Mock |
| `LIVE_TEAM` | 团队成员、RBAC、预算、审计、备份、SLO、恢复和外部交付 | Phase 11 以后经真实团队试用验收才可标记为可用 |

Run 创建时必须冻结模式、DSL、AgentDefinition、Prompt/Tool 版本、Provider、预算、权限、Workspace 基线和输入。前端永远只是后端事实的投影。

### 1.3 不承诺的范围

长期计划不把公网任意不可信代码执行、物理隔离多租户、通用 Agent 市场、无限 Provider 矩阵、移动端、任意 Shell 平台或微服务拆分作为默认目标。只有真实负载、隔离需求或组织合规要求证明必要时，才允许改变这些边界，并先记录 ADR 和替代方案。

## 2. 当前基线与客观判断

以下是 2026-10-17 工作树的静态盘点；测试数字由 Phase 9 全量门禁实测获取：

| 项目 | 当前事实 | 解释 |
| --- | --- | --- |
| 后端生产 Java | 400 个文件 | 已具备较完整的模块化单体，文件数量冻结收敛 |
| 后端测试 | 62 个测试类 | 实测 387 个测试用例全部通过（含 AOCI 诚实性状态机校验） |
| 前端 | 39 个 TS/TSX、1 个 CSS | 生产构建通过，静态生成 4/4 路由；`types/index.ts` 仍偏大 |
| Web 层 | 14 个 Controller | 13 个 ACTIVE，1 个 COMPATIBILITY；Phase 9 登记的 4 个 DELETE_CANDIDATE 已于 2026-10-24（Phase 10 R1）物理退场 |
| 数据库 | 12 个 Flyway 迁移 | 22 张业务表的历史演进已较完整，全部处于 ACTIVE 状态 |
| Markdown | 23 个受管文件 | 13 个 ADR、API/E2E/架构/能力/运维/白皮书等职责已明确收敛归位 |
| 外部参考 | `reference-shrimp-crab/` 为被忽略的参考仓库 | 不进入 AgentHub 构建、AOCI Managed Scope 或发布包；不因为参考存在就复制功能 |

当前最重要的产品问题不是缺少页面，而是：

1. Phase 8 仍主要由 H2、静态构建和 Compose 语法证明，真实 Linux、MySQL 8、Docker、Provider、Git 远端和灾备恢复证据不足；
2. AOCI-CODE 尚未完成官方二进制校验、Managed Scope、Baseline、Whole-Index、Receipt/Evidence 和 `verify/check` 对齐；
3. 18 个 Controller、兼容接口、默认演示值和部分旧路径继续制造授权、契约和测试分叉；
4. `JGitWorkspaceManager`、`ArtifactApplicationService`、`DeploymentApplicationService`、`ConversationApplicationService`、`WorkspaceApplicationService`、`RunEvidenceModule` 等大模块把事务、协议、调度、审计和外部副作用挤在一起；
5. Provider、Runtime、Sandbox、Chat 和扩展 SPI 已经很多，但真实团队还没有一条可重复、可定价、可支持的上线路径；
6. 文档和 CHANGELOG 中存在历史状态、当前事实和路线内容混写的风险。

因此，下一阶段先做可靠性与收敛，再做真实团队产品和扩展生态。继续增加 Provider、聊天功能或视觉组件会掩盖问题，暂不批准。

## 3. 不可破坏的工程不变量

1. **单一事实来源**：Run、Step、Approval、Artifact、Deployment、预算和证据的状态只能由后端持久化事实产生。
2. **快照不可静默改变**：运行开始后，DSL、输入、规则、Provider、权限、预算和工作区基线不可被覆盖。
3. **至少一次执行，幂等副作用**：每个异步动作必须有 attempt、租约、超时、取消、幂等键、审计和补偿；不宣称分布式 exactly-once。
4. **高影响工具先计划后确认**：写文件、执行构建、网络访问、部署、外部交付和凭据使用必须有 allowlist、schema、角色、预算、审计和可恢复失败。
5. **真实与模拟严格分离**：`DEMO_SIMULATED` 的结果不能进入真实成功、成本、SLO 或上线统计。
6. **模块化单体优先**：Controller 只负责协议和权限入口，Application 编排用例，Domain 承载规则，Infrastructure 承载数据库、文件、Provider、进程和外部系统。
7. **认知不等于摘要**：AOCI Whole-Index、源代码、测试、审批、错误和运行证据各自有职责；任何摘要都不能替代原始证据。
8. **跨平台门禁有效**：Windows 单次通过不能代替 Linux + MySQL 8 + Docker 验证；后台线程、测试数据、租约和全局状态必须在测试后清理。

## 4. 瘦身策略与量化边界

瘦身的目标是降低行为分叉、认知成本和发布风险，不是为了追求低行数。所有删除都必须有调用证据、替代路径、回滚方式和回归测试。

### 4.1 代码瘦身

- 先生成 Controller、路由、DTO、Repository、Provider、Runtime、Sandbox、脚本和测试的引用矩阵；没有生产调用、没有测试价值、没有明确迁移用途的适配器进入删除候选。
- 只保留一个生产接口。旧接口最多保留一个有期限的迁移响应和计数器，迁移期结束后删除 Controller、DTO、测试和文档引用。
- 对大类做“深模块”拆分：外部接口减少，复杂事务、路径保护、JGit、序列化和审计逻辑隐藏在内部模块；禁止为了拆文件建立只有一行转发的包装层。
- 默认值、演示种子、`user-1`/`proj-default`/`run-default` 等必须只存在于显式 demo profile 和测试夹具，生产代码不得依赖。
- Provider、Tool、AgentDefinition、WorkflowTemplate、DeploymentTemplate 每增加一个实现，必须同时增加实际用户旅程、契约测试、成本和停用条件；否则删除或隔离到参考区。
- Phase 10 结束时，所有兼容 Controller 都应有删除结果；生产主链路不得依赖兼容过滤器。大于 600 行的生产 Java/TSX 文件必须有拆分 ADR 或在该阶段完成收敛，行数不是唯一验收指标。

### 4.2 文档瘦身

- `AGENTS.md` 是项目级系统规约，本文是唯一长期路线；不要恢复 `GEMINI.md`、旧长期计划或第二份系统提示词。
- README 只保留入口、产品主线、模式和快速验证；当前架构放 `ARCHITECTURE.md`，接口放 `API.md`，能力真相放 `CURRENT_CAPABILITY_BOUNDARIES.md`，演练放 `E2E_DEMO_GUIDE.md`，运维放 `docs/ops/`，不可逆决策放 ADR。
- 一个事实只在一个文档中作为权威来源，其他文档只链接，不复制完整表格、阶段记录或历史数字。
- 白皮书只保留答辩需要的取舍；如果内容不能指导用户、维护者或运维人员，删除或移入 Git 历史。
- 阶段验收只追加到本文对应的阶段记录，禁止每个阶段新建一个长期计划、总结或“最终版” Markdown。
- Phase 9 结束时形成 Markdown inventory、引用检查和删除清单；目标是将 26 个活跃 Markdown 收敛到约 18～22 个，不牺牲安全、接口、运维和 ADR 事实。CHANGELOG 只记录历史变更，不再承担当前能力说明。

## 5. AOCI-CODE 使用边界

AOCI-CODE 的正式仓库已完成只读审视。应吸收的原则是：Git 忽略规则作为 Safe Inventory 权威、Scope 和 Baseline 先于索引、Whole-Index 交付有完整链、认知变更绑定 Receipt/Evidence、`verify/check` 失败时禁止宣称 aligned。不得复制 AOCI 的内部 Plan、Stage、CAS、Recovery 或 MCP 状态机到 Java 业务代码。

Phase 9 的正式接入必须遵循官方发布或可复现源码构建流程：

1. 将经校验的 `aoci`/`aoci.exe` 放在稳定绝对路径，记录版本、来源、构建输入和校验结果；
2. 用 `init` 和 `scan` 建立 Code Cognition，明确排除 `target/`、`node_modules/`、`.next/`、构建产物、运行数据、参考仓库、凭据、日志和宿主配置；
3. 如启用数据库认知，先提供只读 Schema Evidence，再建立 Database Cognition；不得读取业务数据或保存 Provider 凭据；
4. 由受支持的 MCP Host 建立 Whole-Index，完成 Host delivery、Attestation、Baseline 和治理对齐；不手工伪造 `aoci.txt`、Receipt、Ledger 或 `aligned`；
5. 代码和测试稳定后维护受影响 Entry，CI 使用官方 `verify`/`check`，未对齐时阻断高影响修改；
6. AOCI 不可用时明确记录阻塞，继续的工作只能依赖源码、测试和其他真实证据，不能把 Markdown 摘要写成 AOCI 结果。

## 6. 24～36 个月阶段路线

### Phase 9：可信基线、AOCI 接入与瘦身冻结（6～8 周）

**目标**：让团队和 Agent 看到同一份当前事实，停止继续膨胀。

**必须完成**：

1. 重跑后端全量门禁、前端类型/生产构建、Compose、真实 MySQL/Flyway CI 和现有 E2E，更新本文件的静态数字与证据日期；
2. 完成 AOCI 官方安装、Scope、Code/Database Cognition、Baseline、Whole-Index、Receipt/Evidence、`verify/check`，并把 MCP 配置、版本和恢复方式记录到运维文档；
3. 生成代码、路由、数据库表、Provider、Runtime、Sandbox、脚本和 Markdown inventory，标记 `ACTIVE`、`COMPATIBILITY`、`DEMO_ONLY`、`TEST_ONLY`、`DELETE_CANDIDATE`；
4. 盘点默认值、旧接口、localhost、固定路径、重复 DTO、未使用适配器、巨型测试夹具、失败日志和参考仓库边界；
5. 建立产品术语和能力矩阵，只修正事实与链接，不借此新增功能。

**禁止**：新增 Provider、模板、聊天玩法、数据库表、微服务或 UI 重构。

**退出证据**：AOCI 能证明当前受管范围和 aligned 状态；全量门禁有新日志；inventory 有 owner、引用、替代路径和删除顺序；文档旧引用为零；未解决的基线漂移会阻止 Phase 10。

### Phase 10：核心内核收敛与兼容路径退场（10～14 周）

**目标**：把当前代码量变成可长期维护的深模块，减少接口和状态机分叉。

**必须完成**：

1. 以 API v1/OpenAPI 为唯一生产契约，迁移并删除旧 Workspace、Workflow/Diff、Run Alias 和其他兼容 Controller；前端删除旧调用与重复 API 包装；
2. 将 Workspace/JGit、Artifact/Diff、Deployment 生命周期、Run Evidence、Conversation 和 Ops 大类按用例拆成深模块，保留少量稳定接口；
3. 合并重复状态判断、错误码、分页、ETag、幂等和资源归属校验；所有状态跃迁回到统一状态图；
4. 迁移演示种子到 demo profile/测试夹具，生产启动不创建固定主体、项目、运行或 Provider；
5. 对异步执行、SSE、JGit、备份恢复和测试清理补充跨平台回归，删除无价值的过度集成测试和重复 fixture。

**退出证据**：生产主链路不依赖兼容 Controller；调用矩阵只剩一套接口；大模块有拆分前后行为证据；删除代码和文档的 diff 可回滚；全量门禁和 Linux + MySQL CI 通过；AOCI 重新 aligned。

### Phase 11：真实团队工作区与外部交付（12～16 周）

**目标**：让 `LIVE_TEAM` 成为可试用产品，而不是模式枚举。

**必须完成**：

1. 完成一个 Git 提供商的 OAuth/凭据引用、仓库连接、分支策略、Webhook 验签、变更同步和撤销授权；凭据只保存引用和审计信息；
2. 完成 Project/Team/Member/Role/Workspace 的生命周期、邀请和离职处理，所有资源查询和动作走同一权限矩阵；
3. 将 PR 草稿扩展为受控外部交付：用户确认目标、分支、标题、描述和变更摘要后才能创建；回调状态必须幂等并进入 Evidence；
4. 提供项目级预算、并发、Workspace 容量、数据保留和 Provider 使用策略；超限显示可恢复原因；
5. 以 2～3 个真实仓库和至少两个不同角色完成试用，记录失败、人工干预、成本和恢复数据。

**禁止**：通用市场、无限 Git 平台、自动合并、无人审批的生产部署。

**退出证据**：真实远程 Git、OAuth/Webhook、PR、撤销、回调重放、权限越权、预算超限和灾备恢复均有目标环境证据；至少一条真实团队旅程完成并导出可审查证据包。

### Phase 12：Agent 质量、上下文与成本运营（12～16 周）

**目标**：把 Agent 从适配器集合变成可版本化、可评测、可定价的工作单元。

**必须完成**：

1. 建立 AgentDefinition、Prompt、Tool、Context Source、质量门禁、停止条件和重试策略的版本注册和快照；
2. 建立离线评测集、对抗输入集、真实脱敏回放集和版本回归报告，区分计划质量、代码质量、测试质量、风险误报和成本；
3. 将 Context Assembly 与 AOCI Whole-Index、受影响文件、运行事件和错误证据绑定，记录 token 预算、截断理由和来源；
4. 完善 Provider 路由、缓存、超时、熔断、重试和预算预估，提供按项目、模板、Agent 和 Provider 的实际成本报表；
5. 让用户能比较不同 Agent/Provider 版本的成功率、人工干预率、耗时和成本，但不把离线分数写成生产承诺。

**退出证据**：每次 LIVE Run 都有可重放的策略快照；评测结果能阻止明显回归；预算阻断无绕过路径；上下文过期、证据缺失和 AOCI 未对齐会显示为风险或阻塞；成本统计与 Provider 账单抽样一致。

### Phase 13：受控执行平面与规模化可靠性（14～20 周）

**目标**：在证据证明需要时，把单节点执行演进为可恢复的 Worker 平面。

**必须先证明**：Phase 11/12 的真实运行量、队列等待、SSE 连接数、JGit IO、内存、数据库锁和 Provider 延迟已经超过单节点可接受边界。没有数据就不拆服务。

**必须完成**：

1. 优先在模块化单体内形成可独立调度的 Worker/Executor 进程，复用已有数据库租约、Outbox、幂等和状态图；
2. 实现多实例 SSE 回放/广播、任务公平性、租约接管、取消传播、背压、项目级并发和资源配额；
3. 评估对象存储或外部 Artifact 存储，仅在容量和备份证据证明本地文件不足时接入；
4. 将 Sandbox 的网络、文件、进程和清理策略做成可验证的运行配置，明确哪些仍是自托管信任边界；
5. 用故障注入验证断电、进程崩溃、网络抖动、数据库锁、Provider 超时、Worker 重启和重复 Webhook。

**禁止**：为了“看起来云原生”引入空壳消息队列、服务注册中心或没有独立伸缩需求的微服务。

**退出证据**：目标负载下有并发、P95/P99、恢复时间、队列等待、内存和数据库指标；故障注入后无重复副作用、数据丢失或错误成功；部署和回滚手册可由另一名开发者执行。

### Phase 14：生产运营、数据治理与发布工程（10～14 周）

**目标**：让团队可以持续运行、升级和恢复，而不是只能完成一次演示。

**必须完成**：

1. 固化 SLO、告警、值班、关联 ID、审计检索、数据保留、归档和删除审批；
2. 在真实 MySQL 8、Docker、TLS、反向代理和外部 Provider 下完成安装升级、回滚、备份校验、异地恢复和容量测试；
3. 建立依赖升级、漏洞扫描、SBOM、镜像签名/校验、密钥轮换和 Webhook 重放防护流程；
4. 让 Release Manifest、API 兼容策略、数据库迁移、SDK/SPI 版本和弃用窗口进入发布门禁；
5. 把“代码完成、自动化测试通过、目标环境验证通过、生产观察期通过”拆成四种状态。

**退出证据**：至少两次从干净环境安装和升级成功；一次有记录的灾备恢复成功；告警能定位到 Run/Step/Provider/Artifact；发布失败可回滚；数据保留和删除不会破坏审计链。

### Phase 15：受控 Beta、生态边界与维护期（持续 12 个月）

**目标**：以少量真实用户验证产品价值，控制生态扩张速度。

**必须完成**：

1. 选择 3～5 个真实团队或项目进行受控 Beta，定义纳入、退出、数据隔离和支持边界；
2. 只开放已验证的模板、Provider、Git 集成、部署模板和 SDK；每个扩展有契约测试、版本兼容、权限、成本和撤销机制；
3. 建立用户反馈、故障复盘、变更审批、指标周报和月度删除预算；低使用率或高维护成本功能进入退役流程；
4. 维护 README、API、能力边界、E2E、Ops、ADR 和 AOCI 认知资产的单一事实关系，删除过期的 Beta 说明；
5. 每季度只做一次路线复盘：根据真实使用数据调整模板、资源边界和可靠性目标，不因单个演示需求改写长期方向。

**退出证据**：真实用户完成端到端变更交付并愿意重复使用；失败和恢复数据可解释；扩展没有破坏核心不变量；产品可以在没有原作者口头讲解的情况下被安装、使用、审查和恢复。

## 7. 每轮和每阶段的执行合同

每一轮工作都按以下顺序闭环：

1. 读取 `AGENTS.md`、本文、当前阶段验收记录、源码、测试、配置、迁移和 `git status`；
2. 给出本轮一个最小可验证目标，先调查引用和影响，再写 ADR（仅在不可逆、意外且有取舍时）；
3. 实施源码、测试、迁移、配置和文档同步；删除动作先验证绝对路径、调用证据、替代路径和回滚方式；
4. 运行最窄验证，再运行阶段门禁；报告代码完成、自动化测试、目标环境和未验证项四种状态；
5. 重新读取 diff，更新 AOCI 受影响 Entry 和阶段证据；没有 aligned、测试或目标环境证据时不得宣称阶段完成；
6. 每阶段最多一个主交付提交和一个加固提交，提交前遵循仓库 Git 规约。

阶段不能因为“功能已经很多”自动通过，也不能通过新增功能绕开未完成的瘦身、安全、恢复、AOCI 或目标环境门禁。遇到真实外部阻塞时，记录阻塞、影响和不依赖它的低风险工作；不要伪造结果。

## 8. 指标和停止规则

Phase 9 先建立真实基线，Phase 11 以后再设产品目标。至少持续记录：

- 交付成功率、人工干预率、从需求到可审查 Diff 的端到端时间；
- LIVE 与 DEMO 的比例、Provider 失败/重试/熔断、单位 Run 成本；
- 进程重启、租约过期、SSE 断线、Webhook 重放和灾备恢复的成功率；
- P95/P99 API、队列等待、SSE 回放、JGit IO、数据库锁、内存和 Workspace 容量；
- 无证据成功、越权拒绝、过期上下文、未对齐 AOCI、未清理测试数据和未关联 Artifact 的次数。

出现以下任一情况时暂停新增功能，回到 Phase 9/10 的收敛工作：

1. LIVE 运行仍能静默落到 Mock，或前端能制造后端没有的成功状态；
2. 生产主链路继续新增平行接口、重复状态机、固定主体或未使用适配器；
3. AOCI、数据库迁移、备份、审计或证据链无法证明当前事实；
4. 测试出现跨类污染、后台线程泄漏、随机顺序依赖或真实 MySQL 行为差异；
5. 目标环境未验证却准备对外宣称上线、稳定版或团队可用；
6. 文档新增速度超过事实收敛速度，或同一事实出现多个权威版本。

## 9. 历史基线（Phase 0～Phase 8）

上一份计划的阶段结果只作为历史证据，不再作为未来任务清单：

| 阶段 | 已记录的核心结果 | 当前仍需复核的边界 |
| --- | --- | --- |
| Phase 0 | 产品边界、瘦身清单、债务冻结、默认值守卫 | AOCI 正式 Baseline 尚未完成 |
| Phase 1 | 模块收敛、API v1、归属校验、幂等和分页契约 | 兼容入口仍需退场 |
| Phase 2 | 项目接入向导、受控 Workspace、Context Source | 真实 Git 远端和目标环境仍需验证 |
| Phase 3 | AgentDefinition、Workflow Template、DAG、Orchestrator 计划和工具门禁 | 生产评测和成本闭环仍需深化 |
| Phase 4 | 统一状态图、Outbox、上下文记忆、Artifact 血缘和回滚 | 长期运行与多实例证据不足 |
| Phase 5 | 质量门禁、Diff 审查、步骤重做、PR 草稿和证据包 | 外部交付仍主要是草稿 |
| Phase 6 | RBAC、审计、备份恢复、SLO 控制台 | 真实团队试用和异地恢复不足 |
| Phase 7 | 真实 Provider、模式隔离、预算和熔断 | 商业 Provider 长周期压测不足 |
| Phase 8 | E2E、性能基准、Webhook、Release Manifest、SDK/SPI | Linux/MySQL/Docker、千级 SSE 和真实发布尚未充分验证 |

## 10. 阶段证据模板

每个阶段完成后只在本文追加以下记录，不创建新的长期计划文件：

```text
阶段：Phase X
目标：
范围：
代码完成：是/否，关键模块与删除项：
自动化测试：命令、环境、类数/用例数、失败与重试：
目标环境验证：环境、数据、结果；未验证项：
AOCI：版本、Managed Scope、Baseline、Receipt/Evidence、verify/check：
瘦身结果：删除/合并的代码、接口、脚本、Markdown 及引用检查：
指标证据：
已知风险和回滚方式：
阶段退出条件：是/否，原因：
下一阶段唯一入口：
```

### Phase 9 阶段验收记录 (2026-10-17，2026-10-24 更新)

```text
阶段：Phase 9：可信基线、AOCI 接入与瘦身冻结
目标：让团队和 Agent 看到同一份当前事实，停止继续膨胀。
范围：基线验证、AOCI 诚实记录与虚假逻辑修复、全量只读治理 Inventory 盘点、删除候选清单与退场规约制定、AOCI 官方 CLI 接入与认知条目首批创作。
代码完成：是。修复 RunQueryModule.java 与 ContextSourceApplicationService.java 中无凭据默认 ALIGNED 的占位逻辑；在 ContextMemoryCompressor 增加 determineAociCognitionStatus 与 hasOfficialAociReceipt；新增 AociCognitionVerificationTest 自动化校验。2026-10-24：官方 AOCI CLI 物理供给已就绪并完成 init/scan/Baseline；Phase9BaselineAndGovernanceGuardTest 的 AOCI 断言从"未接入不得存在"改为"官方资产必须随仓库存在"；ci.yml 同步口径；新增 .mcp.json（AOCI MCP 宿主接入）与 docs/AOCI_COGNITION.md（AOCI 资产、版本、接入与恢复唯一权威运维文档）。
自动化测试：
  - 后端：mvn clean test，Windows 11，62 个测试类/387 个用例全部通过，0 失败，0 错误，0 跳过（2026-10-24 守卫断言调整后全量重跑确认，耗时 1m31s）。当日首轮曾出现一次 FullLifecycleEndToEndIntegrationTest @TempDir 清理 IOException（Windows 下 JGit 文件句柄释放时序抖动），单类重跑与全量重跑均通过，已如实记录；
  - 架构与基线守卫：ArchUnitArchitectureTest（10 规则通过），Phase9BaselineAndGovernanceGuardTest（守护通过）；
  - 前端：npm ci（128 包），npx tsc --noEmit（0 错误），npm run build（Next.js 14.2.13 构建通过，静态路由生成）；
  - 编排：docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet（校验成功）。
目标环境验证：2026-10-24 GitHub Actions（ubuntu-latest + 真实 MySQL 8.0 容器 + Docker 镜像与灾备演练门禁）对提交 e4465fd（run 40060494344）与 09baef9 的 CI Pipeline 三个 Job 全部 success：Production Config/Compose/Prometheus/Docker/DR Gate、Backend Tests/MySQL 8 Migration/Concurrency Gate (Java 17)、Frontend Build/Typecheck/Lint Gate。远端目标环境证据已到位。未验证项：真实团队多实例长周期运行。
AOCI：官方 aoci CLI v0.1.0-rc21（commit cfa8e93）已安装并记入 docs/AOCI_COGNITION.md。Code Cognition：586 条全量覆盖，S 覆盖 100%，E 档不符 0/586。Database Cognition：2026-10-24 完成官方 database source/snapshot/baseline 流程（数据源 agenthub-mysql-evidence，元数据只读；本地 docker MySQL 8.4 证据容器经 Flyway V1-V12 迁移建出 22 业务表，与 CI 的 8.0 存在小版本差异已记录），23 条表级 Entry（22 业务表 + flyway_schema_history）经 MCP aoci_maintain/aoci_update_entry 管线分两批全部入库。Whole-Index：普通完整交付 6 块（约 40430 tokens）至 completed=true，host_delivery_confirmation 与 model-cognition-attestation/v1 已提交（Challenge 10/10，cognition_verified）；Attestation 后新增的 Database Volume 将在下一次 Overview 刷新时使认知收据升级为 governed（常规刷新，非阻塞）。治理判定：aoci check 五净（findings=0、governance_aligned=true、structure_valid=true，覆盖 code+database 双域），aoci verify 结构有效。治理对齐历史：observe_change_policy 经所有者亲自批准（事务 00a8b24b）切换为 informational。
瘦身结果：完成 18 个 Controller、路由族、DTO、5 个 Provider、4 个 Runtime、3 个 Sandbox、22 张数据库表、10 个脚本、23 个 Markdown 文档的全量只读治理盘点（Inventory）；排定 4 个删除候选 Controller（IMController, RunAliasController, SandboxController, WorkflowAndDiffController）及其对应 DTO、早期原型适配器的退场清单、Owner、0 前端调用证据、替代路径与原子回滚顺序；Phase 9 冻结物理删除，退场操作移交 Phase 10 执行。
指标证据：后端 400 个生产 Java 类、62 个测试类、387 个测试用例；前端 39 个 TS/TSX 组件；数据库 22 张 Flyway 表；AOCI 受管基线 590 文件、586 条认知条目、code_missing 清零。
已知风险和回滚方式：本地 Database 证据容器（docker MySQL 8.4）需保持可用以支撑 `aoci database verify`；CI/生产环境 MySQL 8.0 与其小版本差异已记录。observe 策略与 Scope 变更均可经官方 preview/approve/apply 事务回滚。业务代码回滚依赖 Git 提交粒度。
阶段退出条件：是。全部退出证据齐备：AOCI 能证明当前受管范围与 aligned 状态（check 五净、governance_aligned=true，覆盖 code+database 双域）；Whole-Index 完整交付与 Attestation 通过；全量本地门禁（62 类/387 用例）与远端目标环境门禁（GitHub Actions 三 Job 对 e4465fd/09baef9 全绿）有新日志；inventory 含 owner、引用、替代路径与删除顺序；文档旧引用清零；无未解决的基线漂移。
下一阶段唯一入口：Phase 10——核心内核收敛与兼容路径退场（10～14 周）。
```
