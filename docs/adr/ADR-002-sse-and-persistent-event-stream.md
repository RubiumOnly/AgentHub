# ADR-002: 采用 SSE 单向长连接与数据库持久化事件流 (Persistent Event Stream)

* **状态 (Status)**: 已采纳 (Accepted)
* **制定者 (Deciders)**: AgentHub 核心架构组
* **日期 (Date)**: 2026-10-07
* **技术背景 (Technical Story)**: `docs/AGENTHUB_LONG_TERM_PLAN.md` 阶段 0~1 协同通信与事件持久化机制设计

---

## 一、 背景与问题陈述 (Context and Problem Statement)

在 AgentHub 平台中，多 Agent 协同是一个持续数十秒至数分钟的异步长程过程。在此期间，服务端需要密集且持续地向客户端推送多种关键事件：
1. 大模型打字机增量 Token 流 (`token`)；
2. Orchestrator 任务拆解与富文本交互卡片 (`card`)；
3. DAG 任务流节点状态转移（`WAITING` -> `RUNNING` -> `SUCCESS` / `FAILED`）；
4. JGit 差异审计报告与补丁事件 (`diff`)；
5. 工具执行日志与看门狗状态 (`log`)；
6. 等待人工审批确认的阻断卡片 (`approval`)。

### 现状痛点
在阶段 0 原型实现中，通信机制存在严重的架构脆弱性：
* **事件纯内存驻留**：服务端通过 `ConcurrentHashMap<String, List<SseEmitter>>` 在 JVM 内存中维持连接与推送，事件完全不落库，无全局序号；
* **断线即丢失**：若用户刷新页面、切换网络或长连接发生超时闪断，连接断开期间的所有服务端推送事件彻底永久丢失；
* **缺乏回放能力**：任务执行完毕后，新加入群聊的成员或重新打开历史会话的用户，无法回溯完整的执行过程时间线。

我们需要确定下一阶段标准的前后端实时流式通信协议与事件可靠性架构。

---

## 二、 决策动因 (Decision Drivers)

1. **交互语义对齐**：业务通信场景本质为**“客户端发出指令 -> 服务端持续下发执行流”**的非对称模式。客户端上行请求（发消息、点审批、取消执行）频率极低，属于标准且需强幂等的命令操作；服务端下行推送（打字机、拓扑日志）频率极高。
2. **连接稳定性与企业网络穿透**：系统需在各种开发机、反向代理（Nginx / Traefik / Kubernetes Ingress）和企业防火墙后顺畅运行，需避免复杂的协议握手与保活负担。
3. **可追溯性与确定性审计**：每一次 Agent 协同执行的历史事件必须具备 100% 可复现与可审计性，支持全链路离线回放。
4. **断线续传能力**：客户端网络重连后，必须能通过标准机制无缝补齐中断期间丢失的增量事件，保持 UI 状态连续性。

---

## 三、 备选方案 (Considered Options)

1. **方案 A：纯内存 SSE 推送 (In-Memory SSE Only - 原型现状)**
   - 维持当前设计，后端仅通过 `SseEmitter` 内存广播，不落库、不支持重放与续传。
2. **方案 B：全双工 WebSocket 通信 (Full-Duplex WebSocket)**
   - 建立双向 WebSocket 连接，客户端上行消息与服务端下行推流均走统一的 WebSocket 帧通道。
3. **方案 C：标准 SSE 单向长连接 + 数据库持久化事件流 (`run_events`) (选定)**
   - 客户端上行使用标准 RESTful HTTP POST 接口（天然支持参数校验、Spring Security 鉴权、幂等性防护与 OpenAPI 规范）；
   - 服务端下行推流采用标准 HTTP Server-Sent Events (`text/event-stream`)；
   - 核心规则：**持久化先于推流 (Persist-Before-Emit)**。所有事件必须先入库保存并生成单调递增的序列号 (`sequence`)，客户端重连时使用 `Last-Event-ID` 或 `afterSequence` 参数执行增量回放。

---

## 四、 决策结论 (Decision Outcome)

**采纳方案 C：标准 SSE 单向长连接 + 数据库持久化事件流**。

### 核心实施规范

1. **事件持久化契约 (`run_events` / `conversation_events`)**：
   - 每条事件实体包含字段：
     * `id`: 主键 UUID
     * `run_id`: 所属执行 Run 的唯一标识
     * `conversation_id`: 所属会话 ID
     * `sequence`: 同一执行或会话内单调递增的长整型序号 (1, 2, 3...)
     * `event_type`: 事件类型枚举 (`TOKEN`, `CARD_STATUS`, `DIFF_PATCH`, `TOOL_LOG`, `APPROVAL_REQUIRED`)
     * `payload`: 结构化 JSON 载荷
     * `created_at`: 事件产生精确毫秒时间戳
2. **持久化先于推流 (Persist-Before-Emit 范式)**：
   - 领域服务或执行引擎生成事件时，必须在事务中先将事件持久化至数据库，随后触发内部事件总线推送到连接池中的 `SseEmitter`。
3. **断点续传与重放协议**：
   - 客户端建立 SSE 连接时（`GET /api/executions/runs/{runId}/stream` 或别名 `GET /api/runs/{runId}/stream`，通过一次性 `ticket` 票据鉴权），可携带 HTTP Header `Last-Event-ID: 128` 或查询参数 `lastEventId=128`；
   - 服务端首先执行 SQL 查询：`SELECT * FROM run_events WHERE run_id = :runId AND sequence_num > :afterSeq ORDER BY sequence_num ASC`，按单调递增序号补发已落库的历史增量事件；
   - 补发完毕后，平滑衔接进入当前节点内存 `SseEmitter` 实时推流通道；
   - 每个 SSE 数据块均按照 W3C 规范输出：
     ```http
     id: 129
     event: TOKEN
     data: {"token": "public class UserService"}
     ```
4. **客户端上行与操作通道**：
   - 用户的发送消息、确认审批卡片、终止执行等操作继续走独立的 RESTful POST 接口，享受标准的 HTTP 状态码、统一异常捕获与拦截器鉴权。

### 正面收益 (Positive Consequences)

* **确定性增量回放与断点续传**：在单节点部署或连接重连至同一数据库时，客户端只需携带已接收的最大 `sequence_num`（`Last-Event-ID`），即可从 `run_events` 持久化表精确补齐已提交事务的增量事件，避免重复消费与顺序错乱。
* **原生 HTTP 协议兼容性**：SSE 基于标准 HTTP 传输，与浏览器原生 `EventSource` / `fetch` 兼容；自然穿透 Nginx 反向代理（配置 `proxy_buffering off`），无需 WebSocket `Upgrade` 握手。
* **清晰的单向数据流与职责划分**：上行接口保持标准的 RESTful 无状态与安全审计；下行流保持单向订阅，符合 CQRS 读写分离原则。

### 负面代价与已知边界 (Negative Consequences & Known Boundaries)

* **单机内存广播边界（非跨节点分布式总线）**：当前 `RunEventBroadcaster` 的实时连接池（`SseEmitter`）驻留在单节点 JVM 内存中。若未来扩展为多实例水平集群且未引入 Redis Stream / Kafka 跨节点广播，连接到实例 B 的客户端只能通过 `GET /api/executions/runs/{runId}/events?afterSeq=...` 轮询或重连回放获取实例 A 写入数据库的事件。
* **事件表存储增长**：高频 Token 事件会增加 `run_events` 表行数，需配合归档或脱敏运行证据包（Evidence Bundle）导出后定期清理。

---

## 五、 各备选方案优缺点对比 (Pros and Cons of the Options)

### 方案 A：纯内存 SSE 推送
* 优势：开发成本极低，代码量少。
* 劣势：
  * 无持久化，页面一刷即白屏，断线无法恢复；
  * 多节点部署时完全无法跨实例通信（无分布式广播）。

### 方案 B：全双工 WebSocket 通信
* 优势：支持双向全双工通信，延迟低。
* 劣势：
  * 引入额外的状态维护与心跳机制，断线重连逻辑繁琐；
  * 企业防火墙与反向代理经常截断长生命周期的 WebSocket 连接；
  * 鉴权与 HTTP 体系脱节（握手阶段后难以套用标准 Spring Security Filter 链）。

### 方案 C：SSE + 数据库持久化事件流 (选定)
* 优势：协议开销小、网络穿透性极佳；天然具备事件溯源 (Event Sourcing) 与断线补发能力；与现有 RESTful 架构高度协同。
* 劣势：需设计事件持久化模型与增量回放逻辑。

---

## 六、 参考链接 (Links & References)

* `docs/AGENTHUB_LONG_TERM_PLAN.md` - 阶段 1：状态可恢复性与持久化事件流
* `docs/API.md` - 当前 SSE 事件流接口文档
* WHATWG Server-Sent Events Specification (https://html.spec.whatwg.org/multipage/server-sent-events.html)
