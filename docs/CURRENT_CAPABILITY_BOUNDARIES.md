# AgentHub 当前真实能力边界与架构现状白皮书

> **文档版本**: 阶段 0 基线事实脱水 v1.0  
> **生效日期**: 2026-10-07  
> **适用范围**: AgentHub 当前主线工程 (`agenthub-backend` & `agenthub-frontend`)  
> **编制原则**: 100% 诚实、代码事实驱动、拒绝技术包装、明确能力边界

---

## 一、 系统当前真实定位

**AgentHub** 当前处于 **“具备 IM/DAG/JGit/SPI 基础骨架的 Java 多 Agent 协同研发实验系统”**（对应《AgentHub 长期产品化演进路线》中的**阶段 0：止血、冻结范围与基线治理期**）。

它已经成功验证了以下核心链路：
* 基于 Java 17 + Spring Boot 3.3.4 的后端服务与 Next.js 14 前端控制台的双端联调；
* 统一 Agent SPI 规范下对本地 CLI 进程与 DeepSeek HTTP API 的适配与协同；
* 基于 Eclipse JGit 原生内核的行级代码差异审计与 Unified Diff 补丁生成；
* 基于 Spring MVC 与 SSE 的 IM 协同会话管理、@Mention 指令路由与三阶段任务拆解卡片。

**特别声明**：当前系统**尚未达到生产级沙箱隔离、多租户安全防护与企业级云原生持续交付标准**。本文档旨在全面穿透系统表象，如实记录经代码验证的能力与现存的六大真实能力边界。

---

## 二、 经代码验证真实具备的能力 (What IS Working)

以下能力经由 `agenthub-backend`（13/13 绿灯单元测试）与 `agenthub-frontend`（Next.js 生产构建通过）严格交叉验证：

1. **工程骨架与模块分层**：
   - 后端严格遵循 DDD 四层架构（`adapter`, `application`, `domain`, `infrastructure`），采用模块化单体架构形态（参见 [ADR-001](adr/ADR-001-modular-monolith-architecture.md)）；
   - 前端采用 Next.js 14 App Router + Tailwind CSS 构建了 Bento Grid 风格的现代工作台。
2. **统一 Agent SPI 适配层 (`UnifiedAgentAdapter`)**：
   - 具备调用 Anthropic Claude Code、OpenAI Codex、OpenClaw 等本地 CLI 异步子进程管道的能力，集成了进程看门狗超时回收；
   - 具备通过标准 HTTP 客户端直接调用 DeepSeek V3 官方 API 的能力；
   - 实现了对输出文本中敏感 API Key（`sk-...`）的正则过滤脱敏。
3. **基于 JGit 的原生行级代码差异审计**：
   - 后端集成 Eclipse JGit 6.8 核心，能自动初始化受控工作区 Git 仓库，真实对比 Working Tree 与 Baseline 分支，精确输出包含增删行统计（如 `+26 / -95`）的标准 Unified Diff 补丁。
4. **IM 协同总线与任务拆解卡片**：
   - 实现了基于 Spring MVC 的多会话创建与历史消息加载；
   - 具备 `@Mention` 指令解析器，支持 `Orchestrator` 智能体将用户长程需求拆解为三阶段（后端、前端、QA）结构化交互卡片并持久化至数据库。
5. **工作区单机防脏写锁**：
   - 基于 `ReentrantLock` 实现了针对工作区路径的读写互斥锁管理器 (`WorkspaceLockManager`)，能有效防止单 JVM 内部多工作线程并发写入造成的文件截断。

---

## 三、 六大核心真实能力边界深度剖析 (The 6 Reality Boundaries)

为了向研发团队、开源社区与评审人员提供透明可信的真实图景，系统现存的六大能力边界梳理如下：

### 边界 1：沙箱隔离边界 (静态 Nginx Dockerfile 字符串 vs 生产容器沙箱)
* **代码真实事实**：
  查看 `agenthub-backend/src/main/java/com/agenthub/domain/sandbox/service/PreviewSandboxService.java` 代码：
  - `getPreviewHtml` 仅根据项目是否存在预制 `index.html` 返回固定文本，若不存在则返回一段内联的 Tailwind CSS 静态 HTML 模版；
  - `deployProject` 仅通过 Java 字符串拼接生成一段基础的 `FROM nginx:alpine ...` 文本，并生成包含端口 `8080` 的 `DeploymentManifest` 实体（状态直接置为 `DEPLOYED`）。
* **当前真实边界**：
  **系统目前完全没有接入任何物理 Docker Daemon 守护进程！**
  既没有调用 Docker 远程 API，也没有调用 `docker build` 编译镜像，更没有拉起任何轻量级微容器或 WebAssembly/Node.js 隔离沙箱。当前所谓的“实时沙箱预览与部署”，实质上是**“静态 Web 页面模版预览”**与**“静态 Dockerfile 文本生成器”**。
* **规划演进路线**：
  阶段 7 将正式引入 Docker Engine API 客户端，拉起真正的轻量级隔离容器，分配动态映射端口，提供真正具备网络与进程隔离的 Web 预览沙箱。

---

### 边界 2：工作区与安全边界 (受控 WorkspaceResolver 落地现状与演进)
* **代码真实事实**：
  在阶段 0 治理前，`WorkflowAndDiffController` 的所有文件接口（`/api/workspace/files`, `/diff`, `/file/content`, `/file/save`）直接接受客户端传入的 `path` 或 `filePath`，并在服务端直接通过 `new File(path)` 执行 I/O；前端甚至硬编码了 Windows 本地绝对路径 `d:/work/agenthub/data/workspaces/default`。
* **当前真实边界**：
  系统在阶段 0 正在集中引入 `WorkspaceResolver` 安全解析机制（参见 [ADR-003](adr/ADR-003-workspace-distrusts-client-paths.md)），收敛客户端入参为 `workspaceId` + `relativePath`，防御 `../` 目录穿越、绝对路径越权与 `.git` 目录篡改。
  但系统**当前仍直接依赖宿主机文件系统**，尚未建立虚拟文件系统 (VFS)、用户配额限额与基于 S3 的对象存储抽象，工作区尚不具备跨物理节点的分布式挂载能力。
* **规划演进路线**：
  阶段 2 将全面深化受控工作区安全，增加文件读写审计日志流，支持工作区快照的一键版本回滚。

---

### 边界 3：交互与事件流边界 (SSE 单向流 vs WebSocket，内存发布 vs 持久化队列)
* **代码真实事实**：
  后端 `/api/agents/chat/stream` 使用 Spring MVC 的 `SseEmitter` 实现，连接对象保存在服务端的 `ConcurrentHashMap<String, List<SseEmitter>>` 内存中；事件在被推送后即被抛弃，未持久化写入数据库事件表。
* **当前真实边界**：
  1. **无事件持久化**：事件流在网络闪断或浏览器刷新时会产生永久丢失，客户端无法通过 `Last-Event-ID` 回放中断期间的增量事件；
  2. **非全双工**：系统采用的是 SSE 单向流而非 WebSocket，客户端的上行操作（审批、取消、发消息）必须通过独立的 RESTful POST 请求完成；
  3. **无分布式广播**：内存连接池无法在多实例部署下实现跨节点集群事件广播。
* **规划演进路线**：
  阶段 1 将按照 [ADR-002](adr/ADR-002-sse-and-persistent-event-stream.md) 落地 `run_events` 数据库持久化事件表，支持基于序列号的断点续传与离线全流程回放。

---

### 边界 4：数据持久化与认证边界 (单用户本地开发 vs 多租户 RBAC)
* **代码真实事实**：
  后端所有 Controller 端点未配置 Spring Security 过滤器链或鉴权拦截器；会话创建与查询中用户 ID 统一硬编码为默认值 `user-1`；默认开发环境使用 H2 内存/文件数据库。
* **当前真实边界**：
  **系统当前为严格的“单用户本地开发与实验评估环境”**。
  目前完全不具备企业级多用户登录注册、JWT 凭证校验、组织机构树、项目归属授权及 RBAC 角色权限模型；所有能够访问后端 HTTP 端口的客户端均具有对当前运行实例的完全控制权。
* **规划演进路线**：
  阶段 1 引入 Spring Security + JWT 鉴权框架，引入 Flyway 数据库版本演进，正式建立基于 MySQL 的多租户与组织权限数据模型。

---

## 边界 5：模型与执行真实性边界 (Mock/降级标记 vs 生产 LLM API)
* **代码真实事实**：
  `SpringAiApiAdapter` 与 `CliProcessAdapter` 内置了基于关键字匹配的模拟响应逻辑（`generateIntelligentResponse` 与 `generateFallbackOutput`）。在开发者未提供 `DEEPSEEK_API_KEY` 或未安装 CLI 时，会自动返回预设的 Java/TypeScript 示例代码。
* **当前真实边界**：
  早期实现未对模拟输出进行元数据标记，容易造成“真实与模拟混淆”的假象。
  当前系统在阶段 0 治理中确立了 [ADR-004](adr/ADR-004-mock-runtime-for-test-and-degraded-demo.md)：
  - 明确将 Mock 降级严格限制在离线测试与开发演示；
  - 生产环境强制关闭降级；
  - 执行结果必须携带 `simulated=true` 标识。
  同时，当前系统**尚未接入 Spring AI 原生的 Tool Calling / Function Calling 自主多轮回调循环**，复杂任务主要依赖 Orchestrator 的结构化阶段分解。
* **规划演进路线**：
  阶段 3~4 引入具备工具调用能力、长程可恢复状态机与严格可观测性的生产级 `AgentRuntime`。

---

### 边界 6：构建与部署边界 (清单导出 vs 真正 CI/CD 自动化流水线)
* **代码真实事实**：
  前端页面上的“一键部署”按钮，仅向后端发送 POST 请求 `/api/sandbox/deploy/{projectId}`；后端在校验后组装一段包含 Nginx 配置与工作区文件 COPY 指令的 `Dockerfile` 文本，直接返回 JSON 数据。
* **当前真实边界**：
  **系统当前没有接入任何生产环境 CI/CD 自动化部署流水线！**
  它不具备触发 Jenkins/GitHub Actions 执行远程构建、不具备向 Harbor/Docker Hub 推送镜像、不具备生成 Kubernetes Helm Chart 或通过 KubeClient 将 Pod 调度到生产集群的能力。“一键部署”的本质是**“针对前端静态工程的部署清单与 Dockerfile 规格导出”**。
* **规划演进路线**：
  阶段 7~8 接入真正的 Webhook CI/CD 流水线，对接云原生 Kubernetes Operator 与自动化发布监控。

---

## 四、 阶段演进消除路线 (Roadmap for Boundary Elimination)

为系统化消除上述六大边界，项目已在《AgentHub 长期产品化演进路线》中制定了明确的 10 个阶段演进策略：

| 阶段代号 | 阶段目标 | 重点消除的能力边界 |
| :--- | :--- | :--- |
| **阶段 0 (当前)** | **止血、冻结范围与基线治理** | 确立 ADR 决策、消除硬编码路径、落地受控 WorkspaceResolver 雏形、消除盲信任 TLS、建立多环境配置隔离 |
| **阶段 1** | **状态可恢复性与持久化事件流** | 消除边界 3 (落地 `run_events` 表与断点续传) 与边界 4 (引入 JWT 鉴权与 Flyway) |
| **阶段 2** | **受控工作区与 JGit 快照回滚** | 消除边界 2 (全面深化 WorkspaceResolver，支持 Git Commit 快照与一键回退) |
| **阶段 3** | **确定性编排与状态机持久化** | 消除边界 5 (构建带恢复点的 Durable DAG 执行引擎与超时重试机制) |
| **阶段 4** | **统一 AgentRuntime 插件体系** | 消除边界 5 (规范化 Mock 隔离，支持 Function Calling 工具自主循环) |
| **阶段 5** | **多 Agent IM 协同总线与权限卡片** | 消除边界 4 (完善组织群聊、权限细粒度审批与审计流) |
| **阶段 6** | **前端生产级打磨与体验闭环** | 消除边界 3 (前端自适应断网重连、事件增量消费与降级徽章高亮) |
| **阶段 7** | **轻量容器沙箱与本地 Web 预览** | 消除边界 1 与边界 6 (接入真实 Docker Engine API，拉起隔离沙箱容器) |
| **阶段 8** | **生产就绪、部署拓扑与可观测性** | 消除边界 6 (集成 Prometheus / OTEL 监控与标准化 K8s 部署编排) |
| **阶段 9** | **长期演进演练与全链路验收** | 全链路 10 大生产级真实验收场景综合回归验证 |

---

## 五、 关联文档索引 (References & Cross-Links)

* **核心架构决策记录**：
  * [ADR-001: 采用模块化单体架构替代过早微服务化](adr/ADR-001-modular-monolith-architecture.md)
  * [ADR-002: 采用 SSE 单向长连接与数据库持久化事件流](adr/ADR-002-sse-and-persistent-event-stream.md)
  * [ADR-003: Workspace 不信任客户端绝对路径与受控路径沙箱解析](adr/ADR-003-workspace-distrusts-client-paths.md)
  * [ADR-004: 规范 Mock Runtime 边界，仅用于自动化测试与受控降级演示](adr/ADR-004-mock-runtime-for-test-and-degraded-demo.md)
* **顶层实施路线**：
  * [`docs/AGENTHUB_LONG_TERM_PLAN.md`](AGENTHUB_LONG_TERM_PLAN.md) - AgentHub 长期产品化演进路线（10 阶段全景规划）
  * [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) - 核心领域模型与 DDD 四层架构规范
  * [`SECURITY.md`](../SECURITY.md) - 安全规范与敏感凭证治理
