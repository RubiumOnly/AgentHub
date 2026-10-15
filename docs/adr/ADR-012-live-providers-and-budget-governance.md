# ADR-012：Phase 7 真实 Provider 接入、成本治理与生产环境验证

- **状态**：已采纳（Accepted）
- **日期**：2026-10-15
- **关联阶段**：`docs/AGENTHUB_PRODUCT_LONG_TERM_PLAN.md` §9.11 Phase 7（真实 Provider 接入、成本治理与生产环境验证）

## 1. 背景与问题陈述

在 Phase 6 完善了团队协作、RBAC 六级权限与长期运维灾备体系后，AgentHub 具备了企业级交付平台的基础骨架。然而，平台与真实大模型生态的联通、生产环境下的调用成本治理以及生产交付的可靠性防护仍面临以下核心挑战：

1. **真实主流 Provider 接入与工具调用（Tool/Function Calling）标准不统一**：
   原有系统主要依赖模拟 MockProvider 与通用 HTTP 骨架，各主流 LLM（OpenAI、DeepSeek、Anthropic Claude、Google Gemini）在 Function Calling 请求 Schema、响应解析、流式 SSE 协议及退避重试机制上存在格式割裂，缺乏标准统一的 SPI 与客户端适配层。
2. **测试模拟与真实生产环境界限模糊（Silent Mock Fallback 隐患）**：
   在开发调试期允许回退到 MockLlmProvider，但在生产自托管（`LIVE_SELF_HOSTED`）或团队模式（`LIVE_TEAM`）下，若 API Key 缺失或网络异常而静默降级为模拟成功，将向用户伪造代码交付结果，造成重大生产事故。
3. **Token 与成本消耗缺乏精细化核算与硬预算阻断**：
   大模型调用费用随着代码工程分析与多智能体并发急剧上升。缺乏精细化的模型牌价字典、Prompt Cache 命中计费折扣与企业级硬成本阻断看门狗，可能因 Agent 死循环或大吞吐任务导致账单失控。
4. **Provider 服务可用性缺乏主动探测与容灾治理**：
   被动等待调用失败才能感知模型服务抖动，缺乏主动 HTTP 网络延迟探测（Probe）、集群实时健康看板以及支持手动/自愈重置的三态熔断器机制。
5. **生产部署环境硬化与配置规范门禁缺失**：
   生产环境需要严格的环境变量隔离、CI 自动化门禁测试、容器非 root 权限校验与零架构偏差保障。

---

## 2. 决策内容

### 2.1 真实 Provider 客户端适配器规范与标准化工具调用协议

1. **统一 HTTP LLM 基础设施基类（`AbstractHttpLlmProvider`）**：
   - 规范标准 HTTP 请求超时配置（默认 60s，支持按任务覆盖）；
   - 内置指数退避重试算法（Exponential Backoff with Jitter），针对 429 与 5xx 瞬时抖动执行自动补偿；
   - 封装严格模式校验（`isStrictLiveMode`），生产模式下无合法 API Key 立即抛出 `ProviderUnavailableException`。
2. **多厂商工具调用（Tool / Function Calling）适配映射**：
   - **OpenAI & DeepSeek**（`OpenAiCompatibleProvider`）：
     - 注入 `tools: [{type: "function", function: {name, description, parameters}}]` 与 `tool_choice: "auto"`；
     - 结构化解析 `choices[0].message.tool_calls` 为标准 `ToolCall` 领域实体；
     - 流式 SSE 模式下支持解析 delta tool_calls 增量片段；
   - **Anthropic Claude**（`AnthropicProvider`）：
     - 适配 `tools: [{name, description, input_schema}]` 格式；
     - 提取 `content` 数组中 `tool_use` 块，反序列化 `input` JSON 对象为参数；
   - **Google Gemini**（`GeminiProvider`）：
     - 适配 `tools: [{function_declarations: [...]}]`；
     - 提取 `candidates[0].content.parts` 中的 `functionCall` 结构。

### 2.2 运行模式强隔离（Strict Mode Segregation）

系统引入 `ProviderExecutionMode` 枚举并严格隔离三类运行模式：
1. `DEMO_SIMULATED`：本地快速演示与脱机单元测试，允许透明路由到 `MockLlmProvider`；
2. `LIVE_SELF_HOSTED`：标准私有化/单机生产部署，连接真实大模型 API，**严格拒绝静默降级为 Mock**，无可用节点时立即抛出 `NoAvailableProviderException`；
3. `LIVE_TEAM`：企业级多租户协同部署，强制要求合规的 API 凭证与端到端真实执行。

### 2.3 精细化模型单价核算与生产硬预算看门狗（`BudgetGovernanceGuard`）

1. **权威定价目录（`ModelPricing`）**：
   - 登记 OpenAI（gpt-4o: $2.50/$10.00, gpt-4o-mini: $0.15/$0.60, o1: $15.00/$60.00）、DeepSeek（deepseek-chat: $0.14/$0.28, deepseek-reasoner: $0.55/$2.19）、Anthropic（claude-3-5-sonnet: $3.00/$15.00）、Gemini（gemini-1.5-flash: $0.075/$0.30）的标准输入/输出百万 Token 牌价；
   - 支持 Prompt Cache 命中计费折扣核算（`calculateCostWithCache`）；
2. **调用前硬预算拦截（Pre-Invocation Budget Enforcement）**：
   - `BudgetGovernanceGuard` 挂载在 `ProviderDrivenAgentRuntime` 与 `DagAgentInvoker` 入口；
   - 生产硬预算上限默认 $50.00（可通过 `AGENTHUB_BUDGET_HARD_LIMIT_USD` 动态配置）；
   - 当 `当前消耗 + 预估费用 > 预算上限` 时，抛出 `BusinessException(ErrorCode.BUDGET_EXCEEDED)`，立即终止执行；
   - 设定 80% 警戒水位（`warningRatio = 0.80`），前端大盘与日志主动告警；
3. **多维审计汇总与持久化**：
   - 利用 `TokenUsageAuditRepository` 扩展 JPQL 聚合查询，提供按 Model、按 Provider 的 Token 消耗与成本下钻报表。

### 2.4 主动延迟探测（Probe）与三态熔断器动态运维

1. **主动健康检查（Active Probe）**：
   - `POST /api/providers/{id}/probe`：实时发送探测请求并测量物理往返延迟（Latency ms），不依赖业务调用被动采样；
2. **集群健康状态聚合**：
   - `GET /api/providers/health`：输出集群中各 Provider 的可用性、平均延迟、调用量及熔断状态；
3. **动态熔断器查看与重置**：
   - `GET /api/providers/{id}/circuit-breaker`：查询连续失败次数与熔断阈值；
   - `POST /api/providers/{id}/circuit-breaker/reset`：支持人工干预快速恢复 `CLOSED` 状态。

### 2.5 生产部署脚本与 CI 验证配置

1. 建立 `.env.production.ci` 与 `.env.production.example`，明确配置项规范与脱敏规则；
2. 固化 Docker Compose 生产编排文件语法检查；
3. 遵循容器以非 root 用户运行规范。

---

## 3. 产生的影响

### 积极影响
- **零伪造交付**：生产模式下杜绝任何静默回退到 Mock 的行为，真实反映大模型生成效果；
- **成本可控安全**：通过硬预算守卫避免意外高频调用造成财产损失；
- **高可用鲁棒性**：四大家主流模型生态均具备故障重试、熔断与动态降级保护；
- **全链路可观测**：运维大盘与前端实时掌握模型调用费用、Token 统计与网络健康指标。

### 负面影响与应对策略
- **网络依赖**：真实 Provider 依赖外部公网与 API Key。系统提供 `probeProvider` 端点供部署初验，并在缺少密钥时给出精准排查引导。
