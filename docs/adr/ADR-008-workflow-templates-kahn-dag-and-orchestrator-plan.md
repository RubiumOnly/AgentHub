# ADR-008：Phase 3 Agent 工作单元、工作流模板与 Orchestrator 计划产品化

- **状态**：已采纳（Accepted）
- **日期**：2026-10-13
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §7 Phase 3（Agent 工作单元、模板与 Orchestrator 计划产品化）

## 1. 背景与问题陈述

在 Phase 2 完成项目受控接入、工作区配额安全策略与版本化上下文源后，系统进入核心执行治理与 Agent 协同阶段。原有架构存在以下明显短板：

1. **Agent 角色定义与工具边界粗放**：早期 Agent 定义仅为简单的模型包装，缺乏强类型的工具白名单（tools allowlist）、参数 JSON Schema 校验、Provider 选择策略（ProviderPolicy）、单次预算上限（AgentBudget）及安全级别限制。
2. **缺乏开箱即用的工业级工作流模板**：用户无法复用标准的研发流程。缺少针对常见工程场景（如“测试先行的缺陷修复”和“DDD 规范的新增端点交付”）的版本化模板。
3. **DAG 校验缺乏批次编排与极端拓扑防御**：原拓扑排序仅输出单一线性顺序，无法识别并行的执行批次（`executionStages`），且对自环（self-loop）、非连通孤岛节点（island node）和环路（cycle）的检测不够完备。
4. **Orchestrator 缺乏计划生成与高影响工具门禁**：在直接执行前缺少结构化、可审查的计划生成机制（`OrchestrationPlan`）；高影响工具（如 `file_write`、`command_exec`）在未经用户确认前存在被误调用或恶意执行的风险。
5. **长上下文缺乏分层装配与预算截断治理**：没有结构化的分层上下文组装机制，各上下文层（系统规约、AOCI 认知、受影响文件、对话历史、工具输出、用户输入）容易发生 Token 溢出与无序覆盖，缺乏输出配额保护。
6. **Provider 路由缺乏真实活体保护**：在要求真实 Provider 执行的场景下，若无可用 Provider，系统可能静默降级到 Mock Provider，造成虚假交付成功假象。
7. **缺乏离线自动化评测基准**：缺少衡量模型与 Agent 编排效果的自动化 Benchmark，无法量化新端点、修复、重构、测试补齐与恶意注入防御的达成率。

## 2. 决策内容

### 2.1 版本化 AgentDefinition 产品化与内置角色

1. **强契约 Manifest**：引入 `AgentDefinitionManifest`，包含：
   - 工具白名单与 JSON Schema 参数校验；
   - 模型偏好与 ProviderPolicy（首选、备选、容忍度）；
   - Token 预算上限（maxTokensPerRun）、单次调用超时与安全级别；
   - 停止条件（stopConditions）与幂等标识。
2. **预置 4 大规范化研发角色**：系统启动自动确权内置规范角色：
   - `backend-architect`：负责架构评审、DDD 分层边界看护与拓扑拆解；
   - `backend-developer`：负责高质量 Spring Boot / Java 代码实现与领域模型编码；
   - `qa-tester`：负责五维防御用例设计与真实测试门禁验证；
   - `security-reviewer`：负责越权审计、凭据脱敏核验与高影响工具安全审查。

### 2.2 7 内置 Agent 工具体系与输出配额防爆

1. 固化 7 种核心内置工具规范（`ToolRegistry`）：
   - `file_read`（低影响，只读，2MB 保护）；
   - `file_write`（高影响，需要确权授权，1MB 保护）；
   - `command_exec`（高影响，受控命令执行，需要确权授权，1MB 保护）；
   - `code_search`（低影响，代码结构与符号检索，1MB 保护）；
   - `git_operations`（低影响，Git 状态与 Diff 比对，2MB 保护）；
   - `test_runner`（低影响，构建与测试调度，2MB 保护）；
   - `context_inspect`（低影响，上下文指纹检查，512KB 保护）。
2. **`guardOutputSize` 防爆保护**：任何工具输出超过最大配额字节数时，强制在末尾附加 `[TRUNCATED: Output exceeded max allowed size ...]` 标识，防止超大输出击穿上下文与内存。

### 2.3 Kahn 拓扑排序升级与批次编排

1. **多重极值边界校验**：
   - 自环校验（`fromNodeId == toNodeId`）；
   - 环路检测（入度非零即为环路）；
   - 孤岛节点拦截（多节点图中禁止存在无入度且无出度的游离孤立节点）。
2. **并行批次计算（Execution Stages）**：基于入度为 0 的入队波次，计算阶段级并行列表 `List<List<String>> executionStages`，为后续 DAG 并发执行引擎提供确定性波次调度依据。
3. **强制人工审批节点要求**：包含审批意图或处于高危阶段前的工作流节点，必须显式标注 `requiresApproval=true`。

### 2.4 版本化 WorkflowTemplate 与动态实例化

预置两大符合长效研发交付主线的核心模板（`WorkflowTemplateRegistry`）：
1. **`BUG_FIX_WITH_TESTS`**：包含 7 阶段 Kahn DAG（`REQUIREMENT_CLARIFICATION` -> `PLAN` -> `TEST` -> `IMPLEMENTATION` -> `REVIEW` -> `APPROVAL` -> `DELIVERY`），其中审批阶段显式挂载审批门禁，严格绑定 4 大角色。
2. **`NEW_FEATURE_ENDPOINT`**：包含 7 阶段 Kahn DAG（`REQUIREMENT_CLARIFICATION` -> `DOMAIN_MODEL_DESIGN` -> `OPENAPI_SPEC` -> `IMPLEMENTATION` -> `TEST` -> `APPROVAL` -> `DELIVERY`）。
3. 支持动态实例化（`instantiateTemplate`），从模板直接产出带时间戳和幂等版本的 `WorkflowDefinition`。

### 2.5 Orchestrator 计划生成与高影响工具门禁

1. **结构化 Plan 生成（`OrchestratorPlanApplication`）**：在正式执行工作流前，Orchestrator 根据任务目标与模板自动生成包含影响范围（affectedFiles）、执行子任务拆解（subtasks）、预计预算（budget）、质量门禁（qualityGates）与工具权限清单的结构化计划。
2. **高影响工具双重门禁（`enforceHighImpactToolAuthorization`）**：
   - 未经用户确认（`status != CONFIRMED`）时，低影响只读工具允许执行，但一旦调用 `file_write` 或 `command_exec` 等高影响工具，立即抛出 `ErrorCode.FORBIDDEN (4003)` 阻断；
   - 用户审查并确认计划（`confirmPlan`），显式传递勾选的工具授权后，状态置为 `CONFIRMED`，解除阻断，保障执行受控。

### 2.6 6 层上下文分层组装引擎（ContextAssemblyEngine）

建立清晰的认知优先级分层：
1. `SYSTEM_AND_PROJECT_RULES`：系统与项目工程防御规约；
2. `AOCI_COGNITION`：AOCI 软件工程认知基线；
3. `AFFECTED_FILES`：预测变更的文件内容；
4. `CONVERSATION_HISTORY`：上下文历史记录；
5. `TOOL_RESULTS`：前序节点工具执行输出（带防爆截断）；
6. `USER_INPUT`：当前用户的精确指令。

每层独立设置 Token 预算、安全级别（PUBLIC / INTERNAL / RESTRICTED）与截断策略（`TRUNCATE_TAIL` / `SUMMARIZE` / `NONE`），保障装配后的 Prompt 不超配额、来源可溯。

### 2.7 真实活体 Provider 路由保护（No Silent Fallback）

在 `ChatRequest` 中引入 `requireLive` 标志：
- 当 `requireLive=true` 时，Provider 路由器仅选择活跃且已配置的真实 Provider，严禁静默回退至 Mock；若无匹配的活体 Provider，显式抛出 `NoAvailableProviderException`。
- 当 `requireLive=false` 时，允许测试与降级模式使用 MockLlmProvider，明确区分演示与生产。

### 2.8 离线自动化评测基准（OfflineEvaluationApplication）

内置覆盖 5 大典型场景的自动化评测集：
- `NEW_ENDPOINT`：新端点代码生成与契约对齐；
- `BUG_FIX`：状态机边界缺陷修复；
- `REFACTOR`：圈复杂度治理与架构分层规范；
- `TEST_COMPLETION`：边界测试用例补齐；
- `PROMPT_INJECTION_DEFENSE`：对抗恶意 Prompt 越权注入。

输出量化指标（通过率 `passRate`、工具误用率 `toolMisuseRate`、Diff 采纳率 `diffAcceptanceRate`），全绿交付。

### 2.9 架构纪律与零污染守卫

- **零数据表变更**：严格保持 22 张 Flyway 实体表不变，复用已有的 `workflow_definitions` 与 `artifacts` 持久化存储。
- **18 个 Controller 上限**：不新增独立的 Controller 类，扩展已有的 `WorkflowDefinitionController` 与 `AgentRegistryController`。
- **Surefire 字母序与零测试污染**：测试在 `@AfterEach` 中执行级联清理，杜绝异步残留与状态泄漏。

## 3. 影响与收益

- 研发任务拥有可审查、可推演的 Kahn DAG 阶段和 Orchestrator 计划；
- 高影响写操作与命令执行被严格卡在用户确认门禁前，杜绝 Agent 失控风险；
- 长上下文拥有明确的层次与配额兜底，不再发生 Token 爆炸；
- 前后端协同具备统一的模板选择、计划确认模态框与工具授权界面。

## 4. 验证与记录

- `mvn test "-Dtest=Phase3WorkflowTemplatesAndOrchestratorPlanTest"`：5/5 全绿通过；
- `mvn test "-Dtest=Phase9BaselineAndGovernanceGuardTest,ArchUnitArchitectureTest"`：15/15 全绿通过；
- 前端 `npx tsc --noEmit` 与 `npm run build`：0 错误，静态构建完成。
