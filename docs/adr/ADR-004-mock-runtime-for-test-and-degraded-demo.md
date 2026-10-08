# ADR-004: 规范 Mock Runtime 边界，仅用于自动化测试与受控降级演示

* **状态 (Status)**: 已采纳 (Accepted)
* **制定者 (Deciders)**: AgentHub 核心架构组
* **日期 (Date)**: 2026-10-07
* **技术背景 (Technical Story)**: `docs/AGENTHUB_LONG_TERM_PLAN.md` 阶段 0 技术事实脱水与 `ORIGINAL_REQUEST.md` R3 真实能力校准

---

## 一、 背景与问题陈述 (Context and Problem Statement)

在多 Agent 协同系统中，后端需要驱动云端大模型 API（如 DeepSeek V3）或本地进程管道（如 Claude Code CLI、Codex CLI）。
在早期开发与演示验证阶段，为了保证在开发者本地未配置 `DEEPSEEK_API_KEY` 或开发机未安装对应 CLI 命令行工具时系统仍能运行，`SpringAiApiAdapter` 与 `CliProcessAdapter` 内置了“优雅模拟模式”（`generateIntelligentResponse` 与 `generateFallbackOutput`），能够根据用户输入的关键词自动返回预设的 Java/TypeScript 模版代码。

### 历史缺陷与潜在严重风险
然而，这种无边界的隐式 Mock 模式带来了严重的技术隐患与诚信挑战：
1. **隐式降级与假象欺瞒风险**：
   - 当真实 API 调用因网络超时、凭证失效或余额不足报错时，系统在后台静默捕获异常并偷偷切换为模拟器生成假代码；
   - 对外接口与返回数据没有携带任何“此为模拟数据”的元数据字段，导致调用方与用户误以为代码是真实大模型推理生成的，构成了“技术造假与能力夸大”的重大诚信风险。
2. **掩盖生产环境真实故障**：
   - 若线上生产环境发生大模型供应商故障，系统静默降级会导致平台源源不断地向用户仓库写入离线写死的固定模版代码，无法触发生产监控告警，造成灾难性数据污染。
3. **测试套件无法区分真实与仿真**：
   - 现存单元测试在未注入真实 Key 时全部绿灯通过，却无法断言当前测试究竟走的是真实模型交互还是内置 Mock 分支，降低了测试防护网的真实有效性。

---

## 二、 决策动因 (Decision Drivers)

1. **技术真实性与工程诚信 (Engineering Integrity)**：坚决杜绝任何“伪装真实大模型输出”的欺瞒行为；仿真就是仿真，真实就是真实，系统元数据必须 100% 坦诚。
2. **生产故障透明暴露 (Fail-Fast in Production)**：生产环境下，外部依赖故障必须快速失败、记录清晰日志并触发告警，严禁自作主张用假数据污染用户生产工作区。
3. **离线开发与测试确定性**：单元测试与日常原型演示仍需脱离公网网络波动与付费 Token 消耗，必须保留受控、可预测的 Mock 能力。
4. **透明的可观测性 (Observability)**：系统运行指标、健康探针与前端 UI 界面必须具备识别当前运行时状态的能力，并向用户提供明确的视觉感知。

---

## 三、 备选方案 (Considered Options)

1. **方案 A：保持隐式内置 Mock 兜底 (原型现状)**
   - 适配器内部继续保留 `try-catch` 静默降级，不向外部暴露任何标志。
   - *缺陷*：严重违背工程诚信原则，存在生产数据污染致命隐患。
2. **方案 B：完全剔除 Mock 逻辑**
   - 彻底删除所有降级模拟代码，缺失 API Key 或没有 CLI 时直接抛出致命异常中断流程。
   - *缺陷*：日常离线单测无法在断网或无 Key 环境下秒级运行；开源社区开发者克隆仓库后无法进行快速原型体验。
3. **方案 C：架构层物理隔离 Mock Runtime + 显式元数据标定 + 多环境硬隔离策略 (选定)**
   - 将模拟器从生产适配器中物理剥离，抽象为独立的 `MockAgentRuntime` 实现类；
   - 统一响应结果强制打标 `simulated = true` 及降级原因枚举；
   - 生产环境配置强制关闭降级通道；测试与开发环境受控启用。

---

## 四、 决策结论 (Decision Outcome)

**采纳方案 C：架构层物理隔离 Mock Runtime + 显式元数据标定 + 多环境硬隔离策略**。

### 核心实施规范

1. **架构层物理分离**：
   - 抽象统一的 `UnifiedAgentAdapter` 与独立的 `MockAgentAdapter`（或在适配器中严格封装 `isDegraded=true` 状态）；
   - 明确将大模型调用层（真实网络/进程 I/O）与离线测试生成层在包结构和职责上解耦。
2. **执行结果显式打标 (Explicit Metadata Contract)**：
   - 所有执行响应（无论是 REST API 响应还是 SSE 实时事件）必须在载荷中包含明确的真实性元数据：
     ```json
     {
       "content": "public class UserService { ... }",
       "simulated": true,
       "degradedReason": "API_KEY_ABSENT",
       "runtimeType": "MOCK_FALLBACK",
       "model": "mock-simulation-engine"
     }
     ```
   - 若为真实模型生成，则返回 `simulated: false`, `degradedReason: null`, `model: "deepseek-chat"`。
3. **多环境差异化硬隔离策略**：
   - **生产环境 (`application-prod.yml`)**：
     * 配置 `agenthub.runtime.allow-mock-fallback: false`；
     * 当 DeepSeek API 发生网络错误、鉴权失败或配额超限时，**严禁降级**，必须直接抛出 `BusinessException(MODEL_SERVICE_UNAVAILABLE)`，记录 ERROR 级告警日志，向客户端如实返回 502/503 错误状态。
   - **开发与测试环境 (`application-dev.yml` / `application-test.yml`)**：
     * 配置 `agenthub.runtime.allow-mock-fallback: true`；
     * 允许在离线或无 Key 时平滑演示，但在控制台输出清晰的 WARN 日志：`[MOCK_FALLBACK] DeepSeek API Key is absent or unreachable. System is operating in SIMULATED mode for offline demonstration.`
4. **前端工作台透明感知**：
   - 前端接收到 `simulated: true` 标记时，在消息卡片与执行面板顶部显式渲染黄色醒目徽章（如 `⚠️ 演示模式 (离线模拟输出)`），明确提示当前输出非真实大模型生成。

### 正面收益 (Positive Consequences)

* **彻底消除欺瞒合规风险**：系统在技术事实、代码实现与用户感知上达到 100% 透明诚实。
* **生产系统坚如磐石**：阻断了假数据污染生产代码库的可能，确保故障能第一时间被运维监控捕获。
* **保障离线开发敏捷度**：新人在未申请企业 API Key 的情况下，依然能够在 3 分钟内克隆、启动并体验完整的 IM 与工作流交互链路。

### 负面代价与应对 (Negative Consequences & Mitigation)

* **调用链条需传递元数据**：所有适配器、执行器与事件流 DTO 均需扩展 `simulated` 与 `degradedReason` 字段。
  * *应对策略*：在基础层 `AgentExecutionResult` 与 `ChatMessage` 实体中统一增加该字段，保持各模块协议一致。

---

## 五、 各备选方案优缺点对比 (Pros and Cons of the Options)

### 方案 A：隐式内置 Mock 兜底
* 优势：表面上系统永远不报错，“容错率高”。
* 劣势：本质属于技术欺骗，掩盖真实 bug，生产环境极度危险。

### 方案 B：完全剔除 Mock
* 优势：代码绝对纯粹。
* 劣势：日常单元测试难以脱网秒级运行，开源体验门槛极高。

### 方案 C：显式 Mock + 元数据打标 + 环境硬隔离 (选定)
* 优势：兼顾工程真实性、生产安全底线与离线开发便利性；属于业界标准的成熟方案。
* 劣势：增加了多环境配置开销与 DTO 字段维护成本。

---

## 六、 参考链接 (Links & References)

* `docs/CURRENT_CAPABILITY_BOUNDARIES.md` - AgentHub 当前真实能力边界与系统事实白皮书
* `docs/AGENTHUB_LONG_TERM_PLAN.md` - 阶段 0：止血、冻结范围和基线
* Google Cloud: *API Design Guide - Errors & Partial Failures*
