# 更新记录 (Changelog)

所有关键架构升级、特性新增与重要缺陷修复均按版本记录于此。

---

## [v1.9.0] - 2026-10-09

### 🏆 终局里程碑：阶段 9 端到端质量门禁、全链路压测与大厂级产品化交付 (Phase 9 Final Release)
- **生产级可观测性与业务指标系统 (Spring Boot Actuator & Micrometer Prometheus)**：
  - 引入 `spring-boot-starter-actuator` 与 `micrometer-registry-prometheus`，在 `application.yml` 与 `application-prod.yml` 中安全暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`；
  - 落地 `AgentHubMetricsCollector` 指标收集组件：动态注册核心 Gauge / Counter / Timer 指标（活跃 Run 计数 `agenthub.runs.active`、活跃 SSE 订阅数 `agenthub.sse.connections.active`、Step 执行延迟分布 `agenthub.steps.duration`、Token 消耗统计、工作区锁竞争耗时 `agenthub.workspace.lock.wait`、部署成功/失败计数）；
  - `RunEventBroadcaster` 联动指标收集器实时统计活跃长连接；
- **全链路端到端黑盒冒烟与集成验收套件 (`FullLifecycleEndToEndIntegrationTest`)**：
  - 严格覆盖长期规划第 6 节定义的【场景 A、B、C、D、E】五大核心交付场景：
    - **场景 A**：单 Agent 试运行与产物审计（创建项目 -> 启动 Run -> 接收 SSE 真实流 -> JGit 结构化快照审计 -> 产物持久化验证）；
    - **场景 B**：多 Agent 团队协作与 DAG 拓扑门禁（Backend 与 Frontend 并行开发 -> QA 汇聚 -> WAITING_APPROVAL 挂起 -> 审批人通过 -> 恢复调度至 SUCCEEDED）；
    - **场景 C**：不可信输入防御与安全边界（受控路径穿越拦截、高危命令防火墙拦截、敏感 API Key 全链路脱敏）；
    - **场景 D**：任务取消、进程回收与 SSE 断点补发（优雅取消、状态机置为 CANCELLED、基于 Last-Event-ID 游标精准重放）；
    - **场景 E**：JGit 非破坏性回滚与沙箱部署生命周期（回滚至基线快照并保留历史审计、动态端口分配与释放）；
- **高并发、锁竞争与极限压力性能测试套件 (`HighConcurrencyStressIntegrationTest`)**：
  - **压测 1**：工作区排他租约锁竞争（20 个并发线程争抢同一工作区锁，验证 100% 互斥安全，同一瞬间持有者 <= 1，无死锁发生）；
  - **压测 2**：多智能体消息总线 128 分段锁单调定序（50 个并发线程同时争抢序号分配，87ms 内完成分配，严格 1..50 零重复、零跳号、全保序）；
  - **压测 3**：DAG 拓扑调度批量执行吞吐（并发运行 5 个独立复杂工作流，541ms 内全部跑通，沉淀 P50: 252ms, P99: 268ms 性能基线）；
- **生产级多容器编排与基础设施交付 (`docker-compose.prod.yml`)**：
  - `agenthub-backend/Dockerfile`：多阶段构建（JDK 构建 -> JRE 极小镜像），非 root 用户 `agenthub:agenthub` (uid=10001)，JVM 内存自适应参数，内置 Actuator 健康探针；
  - `agenthub-frontend/Dockerfile`：Next.js 14 Standalone 多阶段构建，非 root 用户 `nextjs:nodejs` (uid=1001)，镜像轻量敏捷；
  - `docker/nginx/agenthub.conf`：企业级反向代理与网关，针对 `/api/.*/events` 与 `/stream` 显式配置 `proxy_buffering off`、`proxy_cache off` 与长超时，确保 SSE 零缓冲流式直推；
  - `docker-compose.prod.yml`：整合 MySQL 8.0、Redis 7、Backend、Frontend 与 Nginx 网关全栈容器网络，配置持久化数据卷与资源限额；
  - 提供生产环境变量模版 `.env.production.example` 与一键部署脚本（`scripts/deploy-prod.sh`、`scripts/deploy-prod.ps1`）；
- **大厂级生产运维与灾难恢复规范**：
  - 产出《AgentHub 生产级 Prometheus 告警规则规范》（`docs/ops/ALERTING_RULES.md`），定义 7 类核心告警与 SLA 处置响应分级；
  - 产出《AgentHub 生产故障演练与灾难恢复手册》（`docs/ops/DISASTER_RECOVERY_RUNBOOK.md`），覆盖数据库宕机、僵尸进程排查、锁自动恢复、SSE 断流优化与数据冷备 SOP；
- **核心架构资产与白皮书交付**：
  - 产出《AgentHub 核心技术架构白皮书与大厂级答辩防穿指南》（`docs/TECH_WHITEPAPER_AND_DEFENSE.md`），深入拆解模块化单体、SSE+持久化、JGit 快照三大架构权衡，提供大厂面试官高频 10 问防穿打法；
  - 产出《AgentHub 5~8 分钟全链路真实演示指南》（`docs/E2E_DEMO_GUIDE.md`）；
  - 全面更新《当前真实能力边界最终版》（`docs/CURRENT_CAPABILITY_BOUNDARIES.md`），如实对齐全部 10 大阶段达成事实；
  - 后端 267 项全量测试 100% 绿灯，前端 Next.js 生产构建 100% 成功，长期计划 10 大阶段 100% 圆满交付收官！

### 🔧 调度引擎并发加固与 GitHub Actions CI 稳定性治理 (Bugfix)
- **并发重复分发缺陷治理**：排查并修复 `DagExecutionEngine` 在多核高速并发下因初始就绪节点动态遍历与后继节点提前递减入度产生的竞态条件（Race Condition）；
- **原子调度防重防线**：引入 `scheduledNodes` 并发去重 Set，物理级杜绝任何节点被重复提交线程池；
- **静态拓扑入口发现**：图根节点严格通过入度静态快照发现，彻底消除主线程读与 worker 线程写的数据竞争；
- **汇聚门禁终态可见性等待**：针对菱形 DAG 汇聚节点（Join Gate）增加多核 CPU 内存可见性与前驱终端状态自旋等待，根除偶发误判为 `SKIPPED` 的缺陷；
- **高频循环压测套件**：在 `WorkflowDagSchedulingAndParallelExecutionTest` 中增加连续菱形 DAG 汇聚压测，后端全量 267 项测试 100% 稳固通过。

---

## [v1.8.1] - 2026-10-09

### 🐛 缺陷修复：消除 Linux CI 健康检查 IPv6 解析竞态与远程全量 2/2 绿灯
- **Linux CI IPv4/IPv6 回环解析竞态消除**：
  - 修复 `PortAllocationAndHealthCheckTest` 中 `HttpServer` 与 `HealthCheckProbeService` 使用 `localhost` 在 Linux 双栈环境下偶发连接拒绝的竞态问题，统一明确绑定并探测 `127.0.0.1`，探测宽限重试提升至 5 次；
  - 为 `EnvironmentSanitizer` 增加跨平台基础 `PATH` 兜底（Windows `System32`，Linux 标准 bin 目录），杜绝极端沙箱环境下 PATH 丢失；
  - 优化 `.github/workflows/ci.yml`，在测试异常时自动输出 Surefire 诊断报告，保证全链路透明可追溯；
  - 远程 GitHub Actions CI 两个 Job（`Backend Tests & Build` 与 `Frontend Build & Typecheck`）全部成功通过（Run ID `37879461247`，2/2 绿灯）。

---

## [v1.8.0] - 2026-10-09

### 🌟 阶段 8：前端 Bento 现代美学、实时看板与交互体验升级 (Phase 8 Deliverables)
- **坚决消灭“AI 塑料味”前端，遵循 `modern-aesthetic-ui` 顶级规范**：
  - 彻底重构前端仪表盘，采用 **Bento Grid** 模块化便当盒网格，打造 Linear / Vercel / Apple 级极致精致质感；
  - 彻底杜绝无意义的装饰性副标题、刺眼大渐变与死板假卡片，全量采用低饱和度环境微光（Ambient Subtle Radial Glow）与高阶磨砂微质感（Subtle Glassmorphism：`bg-zinc-900/60`, `backdrop-blur-md`, `border border-zinc-800/80`）；
  - 精致暗黑模式灰阶层级（底色 `zinc-950` / `#09090b`，卡片 `zinc-900`，悬浮 `zinc-800`，单重点色 Indigo / Emerald 锚点，等宽字体呈现所有 Hash、ID、时间戳与成本数值）；
- **全生命周期执行与拓扑看板 (Bento Executive Dashboard)**：
  - **实时执行流面板 (`LiveExecutionTerminal`)**：无缝集成阶段 3 SSE 流式输出（`/api/runs/{runId}/stream` 与 `/api/executions/runs/{runId}/stream`），支持自动滚动开关（Auto-scroll toggle）、ANSI 语法色彩分级高亮（`[INFO]`, `[WARN]`, `[ERROR]`, `[STEP]`, `[AGENT]`, `[AUDIT]`, `[SANDBOX]`）、带 `Last-Event-ID` 游标的断点重连状态指示（`CONNECTED`, `RECONNECTING`, `OFFLINE`）与日志一键复制/清空；
  - **工作流 DAG 依赖拓扑可视化面板 (`DagTopologyVisualizer`)**：基于 `@xyflow/react` 构建现代化流体节点图，全量原生支持阶段 4 Workflow DSL 节点类型（`START`, `AGENT`, `APPROVAL`, `CONDITION`, `JOIN`, `END`），清晰标识 6 维节点状态（`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `SKIPPED`, `WAITING_APPROVAL`），并配备交互式节点属性抽屉（Node Inspector）；
  - **人工审批挂起与决策门禁卡片 (`ApprovalActionCard`)**：当工作流命中 `WAITING_APPROVAL` 门禁时，高优先级微光警告卡片实时浮现，清晰展示审批说明与请求主体，提供审查人与审查意见输入框，通过一键 Approve（批准放行）与 Reject（安全驳回）实时联动后端 REST 接口（`/api/approvals/{id}/approve` 与 `/api/approvals/{id}/reject`）；
  - **受控工作区与 JGit 行级审查面板 (`DiffArtifactReviewer`)**：展示阶段 2 的变更文件树、增删行统计（+added, -deleted）、Unified Diff 行级高亮对比与一键“安全回滚 (Revert to Baseline)”防误触确认交互；
  - **多智能体协同网络与会话消息流 (`MultiAgentSwarmChat`)**：展示阶段 6 的 Team 拓扑（Hierarchical/P2P/Round-Robin）、角色标签（`ORCHESTRATOR`, `ARCHITECT`, `CODER`, `REVIEWER`, `TESTER`）、128 分段锁单调连续保序序号（`#1`, `#2`, `#3`...）、私聊隔离（`BROADCAST` vs `DIRECT` P2P 锁定）、四层死循环熔断指示器（`LoopDetector: Normal`）、上下文滚动摘要（Rolling Summary）与飞书级富文本卡片交互；
  - **模型 Provider 动态路由与 Token 成本仪表盘 (`ProviderCostDashboard`)**：可视化展示阶段 5 多 Provider 矩阵（DeepSeek, Claude 3.5 Sonnet, Gemini 1.5 Flash, Ollama Local, OpenAI Backup）、`avgLatencyMs` 毫秒级延迟、`CircuitBreaker` 三态熔断器状态（`CLOSED` 绿灯, `HALF_OPEN` 黄灯, `OPEN` 红灯）、Token 消耗量与实时成本核算（$），并支持加权路由动态推演模拟；
  - **沙箱预览与部署控制台 (`SandboxPreviewPanel`)**：集成阶段 7 部署生命周期状态机（`CREATED`, `BUILDING`, `RUNNING`, `STOPPED`, `FAILED`）、受控动态端口原子分配（18000-18999）、部署构建日志实时流查看与内嵌式 Web 沙箱安全预览 iframe；
- **前端工程架构解耦与类型安全**：
  - 彻底治理前序轮次中 `page.tsx` 过于庞大臃肿、状态混杂的代码坏味道，拆分为 `src/types/`、`src/services/api.ts`、`src/components/common/` 与 `src/components/dashboard/` 模块化架构；
  - 具备完善的离线/脱机优雅降级与演示种子数据体系，后端未就绪时界面不白屏、不崩溃；后端联通时自动无缝衔接实时接口；
  - 严格 TypeScript 强类型约束，0 `any` 危险断言，Next.js 14 生产打包（`npm run build`）全量路由静态生成 100% 成功通过；
- **全绿灯质量门禁**：
  - 前端 Next.js 14 生产构建 100% 成功（4/4 路由预渲染完成，0 报错）；
  - 后端 255/255 项单元/领域/架构测试 100% 保持全绿，前后端协作零破坏。
- **阶段 8 严苛自检加固与全链路缺陷修复 (Audited Hardening & Defect Fixes)**：
  - **DAG 拓扑补齐与动态状态联动**：补齐阶段 4 DSL `CONDITION` 条件分支节点与 `SKIPPED` 回退分支，修复 `DagTopologyVisualizer` 缺少响应式 `useEffect` 导致在审批通过/驳回后节点状态与连线样式无法动态更新的缺陷；
  - **REST API 全局认证注入**：在 `services/api.ts` 的 `fetchJson` 中自动注入默认 `X-User-Id` 鉴权请求头，根治后端 `AuthFilter` 对未授权调用抛出 401 Unauthorized 的隐患；
  - **工作区资源探测解耦**：彻底重构 `WorkspaceExplorer`，消除 3 处对 `http://localhost:8080` 的硬编码直连，统一收口至 `apiClient`；
  - **实时终端 ANSI 语义解析器**：落地 `renderAnsiMessage` 词法解析器，支持真实 ANSI 颜色转义代码解析与分级渲染，加固连续重试失败后的平滑 OFFLINE 离线状态降级；
  - **多智能体消息多维度过滤**：在 `MultiAgentSwarmChat` 中新增全部、广播流、私聊隔离 (P2P)、协同卡片 4 态过滤器，并动态显示轮次水位；
  - **沙箱安全沙盒与构建状态补全**：为内嵌 iframe 补齐 `sandbox` 安全沙箱隔离策略，为一键部署操作增加 `BUILDING` 状态防重复点击防护，动态对齐 `DEFAULT_PREVIEW_URL`。

---

## [v1.7.0] - 2026-10-09

### 🌟 阶段 7：工作区沙箱容器化与部署自动化 (Phase 7 Deliverables)
- **沙箱隔离体系架构 (`com.agenthub.sandbox.domain.provider`)**：
  - 定义沙箱运行时统一 SPI 接口 `SandboxProvider` 与注册工厂 `SandboxProviderFactory`；
  - 落地受限子进程沙箱 `LocalProcessSandbox`：实现严格的工作目录边界约束、受控环境变量注入与命令执行；
  - 落地容器化沙箱 `DockerSandbox`：精确构建非特权用户（`--user 1000:1000`）、只读根文件系统（`--read-only`）、内存与 CPU 资源硬限制（`--memory`, `--cpus`）、临时目录限制（`--tmpfs /tmp:rw,noexec,nosuid,size=64m`），以及严格限制仅挂载受控工作区（`-v <workspace>:/workspace:rw`）；
- **安全策略与命令防火墙 (`CommandSecurityGuard`)**：
  - 建立全方位高危命令检测与恶意注入拦截矩阵：严格阻断破坏性文件删除（`rm -rf /`, `rm -rf ~`, `rm -rf *`, `del /s /q C:\` 等）、底层磁盘格式化与覆盖（`mkfs`, `dd if=...`, `fdisk`）、远程管道注入（`curl ... | sh`, `wget ... | bash`）、PowerShell EncodedCommand、恶性命令串联注入（`; rm -rf`, `&& rm -rf`）与 Fork Bomb 攻击；
  - 白名单限制可用可执行文件（放行 `node`, `npm`, `mvn`, `java`, `python`, `git`, `echo` 等标准开发工具，严格封禁 `sudo`, `su`, `useradd`, `nc`, `netcat`, `nmap` 等提权与渗透工具）；
- **环境变量隔离与敏感凭证防护 (`EnvironmentSanitizer`)**：
  - 阻断宿主机敏感凭证继承：自动清洗并拦截包含 `KEY`, `SECRET`, `PASSWORD`, `TOKEN`, `CREDENTIAL`, `AUTH`, `DATABASE` 等关键字的敏感变量，防止 API Key 与数据库密码泄露至沙箱子进程；
  - 仅继承标准系统安全基线（`PATH`, `HOME`, `USER`, `LANG`, `TEMP` 等），并阻断 `LD_PRELOAD`, `BASH_ENV` 等动态链接劫持注入；
- **资源配额与看门狗超时监控 (`SandboxResourceQuota` & Watchdog)**：
  - 建立沙箱资源配额模型（最大超时毫秒、最大输出字节数、最大内存与 CPU 核心数）；
  - 落地单命令执行超时看门狗监控：命令超时时自动触发多平台进程树强平（Process Tree Kill，包括 Windows/Linux 子孙进程级级联终止），标记 `isTimedOut = true` 并返回退出码 137；
  - 落地输出缓冲区防撑爆机制（Output Buffer Truncation）：当子进程标准输出/错误流超出配额（如海量死循环打印）时，安全截断内容并追加警示标识，从根源消除海量日志撑爆 JVM 内存 OOM 隐患；
- **代码一键部署与运行预览环境 (`DeploymentApplication` & `DeploymentController`)**：
  - 落地部署领域模型：状态机（`CREATED`, `BUILDING`, `RUNNING`, `STOPPED`, `FAILED`）、部署目标（`STATIC_PREVIEW`, `LOCAL_PROCESS`, `DOCKER_CONTAINER`）与部署规范；
  - 落地线程安全端口分配管理器 `PortAllocationService`：在受控端口范围（18000-18999）内进行原子分配、真实操作系统 TCP 套接字绑定探测、以及全生命周期释放与回收；
  - 落地主动健康检查探测器 `HealthCheckProbeService`：基于 HTTP Client 进行多次重试探测与超时感知，验证部署服务可用性；
  - 暴露标准 RESTful 接口 `DeploymentController` (`/api/deployments`)，支持创建部署、停止部署、查询部署状态、获取执行日志与项目历史部署列表，并向下完全兼容现有 Web 沙箱预览接口；
- **Flyway 数据库版本演进 (`V8__phase7_sandbox_and_deployments.sql`)**：
  - 扩展 `deployments` 部署表：新增 `port`, `build_command`, `start_command`, `health_check_path`, `log_output`, `error_message`, `sandbox_type`, `container_id`, `updated_at`；
  - 建立高性能索引 `idx_deployments_status`, `idx_deployments_proj_status`, `idx_deployments_port`, `idx_deployments_created`；
- **全绿灯防御测试矩阵与架构守护**：
  - 新增 4 大测试套件：`SandboxSecurityGuardAndFirewallTest`、`SandboxProviderAndExecutionTest`、`PortAllocationAndHealthCheckTest` 与 `DeploymentLifecycleAndControllerIntegrationTest`；
  - 更新 `FlywayMigrationAndSchemaTest`，验证 V8 迁移顺利生效；
  - 后端测试套件由 187 项提升至 255 项（255/255 全部通过，0 错误，0 失败）；前端 Next.js 14 生产构建 100% 成功。
- **沙箱内核深度加固与架构治理 (Deep Hardening & Robustness)**：
  - 修复 `LocalProcessSandbox` 在 Windows 环境下看门狗超时强平后因异步句柄释放延迟导致的临时目录锁定问题，引入 `taskkill /F /T /PID` 级联递归终止与 `waitFor` 同步收割，彻底消除文件锁定与资源泄露；
  - 修复 `CommandSecurityGuard.extractBaseExecutable` 遗漏 `.exe` 后缀规范化导致的 Windows 标准工具（`node.exe`, `git.exe`, `python.exe` 等）白名单误杀 Bug；
  - 强化 `CommandSecurityGuard` 命令防火墙正则防护矩阵：支持分立参数（`-r -f`, `-f -r`, `--recursive --force`）、带单双引号路径及 Shell 内部包裹脚本（`sh -c`, `bash -c`, `cmd /c`, `powershell -c`）的高危破坏性指令拦截；
  - 重构 `DeploymentApplicationService.createAndDeploy`：遵循 `java-spring-architect` 生产架构准则，剔除长耗时构建命令及网络健康检查探针对 `@Transactional` 的侵入，防止 HikariCP 连接池耗尽；并在停止部署时联动触发沙箱进程与容器资源回收；
  - 修复 `PortAllocationService` 中 `ServerSocket` 构造器先绑定后调用 `setReuseAddress` 的无效配置问题；
  - 增强 `BoundedOutputReader` 缓冲区并发读写线程安全保护，采用守护线程池防止 JVM 关闭阻塞。

---

## [v1.6.0] - 2026-10-08

### 🌟 阶段 6：多智能体协同网络、消息总线与对话系统 (Phase 6 Deliverables)
- **多智能体团队协同拓扑与角色编排 (`com.agenthub.team`)**：
  - 标准化落地团队协同拓扑策略接口 `TeamTopologyStrategy` 及工厂 `TeamTopologyStrategyFactory`；
  - 落地三大核心协作模式：
    - `HIERARCHICAL`：层级主从拓扑，Leader 智能体集中接收目标、拆解并下发任务，下级成员自动回传汇报，防无序发散；
    - `PEER_TO_PEER`：对等去中心拓扑，全员平等协作，支持基于 @Mention 自主点对点交互；
    - `ROUND_ROBIN`：轮询流水线拓扑，严格按角色链（Architect -> Coder -> Reviewer -> Tester）环形流转；
  - 领域模型支持标准化团队角色 `TeamRole`（`ORCHESTRATOR`, `ARCHITECT`, `CODER`, `REVIEWER`, `TESTER`），支持成员级提示词覆盖 `system_prompt_override` 与委派权限 `can_delegate`；
  - 暴露团队与成员管理及拓扑推演 REST API：`TeamController` (`/api/teams`)；
- **会话消息总线与严格单调递增序号 (`ConversationSequenceManager`)**：
  - 基于 128 分段公平重入锁池 (`Striped ReentrantLock`) 消除内存泄露隐患，并结合数据库悲观写锁 (`PESSIMISTIC_WRITE`) 保障多实例并发安全；
  - 彻底杜绝并发写入时的序号冲突与消息乱序，确保同一会话内 `sequence_num` 100% 严格单调连续递增（1, 2, 3...）；
  - 保障高并发压测下消息时序的绝对一致性与事件时间线确定性；
- **消息路由策略与可见性隔离机制 (`MessageVisibilityFilter`)**：
  - 标准化支持三大消息路由类型：`BROADCAST`（全员广播）、`DIRECT`（定向私聊）、`SYSTEM`（系统事件声明）；
  - 落地严密可见性隔离：第三方 Agent 与未传 viewerId 的匿名请求绝对无法窃视点对点私聊消息，严格限制仅收发双方与特权审计者可见；
- **跨智能体协作协议与三层死循环熔断治理 (`LoopDetector`)**：
  - 规范化跨智能体交互协议 `MessageProtocolType`（`REQUEST_REPLY`, `HANDOFF`, `SUMMARIZE`），通过 `in_reply_to_id` 构建结构化对话依赖树；
  - 建立四层递进防御熔断矩阵：
    - 第 1 层：连续智能体自主协作轮次截断（严格继承 Team `maxTurns` 配置，人机交互不误杀）；
    - 第 2 层：内容哈希指纹碰撞检测（防复读死循环）；
    - 第 3 层：短周期双智能体 Ping-Pong 震荡检测；
    - 第 4 层：三智能体环形死循环 (A-B-C-A-B-C) 与单智能体连续自旋 (A-A-A) 闭环拦截；
- **智能上下文窗口治理与滑动压缩 (`ContextWindowGovernance`)**：
  - `SlidingWindowContextTrimmer`：优先锚定保留系统初始提示词与最近 N 条交互高保真上下文，滑动修剪中间历史；
  - `TokenBudgetContextManager`：基于最大 Token 预算（`maxBudgetTokens`）严格限制上下文膨胀；
  - `RollingSummaryService`：消息超阈值时自动生成连贯滚动摘要（Summary）持久化落库（`conversations.summary`），在下游 Prompt 中注入 `[Previous Conversation Summary]`，节省 70%+ 上下文开销；
- **实时 SSE 事件总线与断点续传重放 (`ConversationEventBroadcaster`)**：
  - 全生命周期推送领域事件，连接关闭自动安全驱逐空会话通道防止内存泄露；
  - 客户端通过 `Last-Event-ID` 头部或 query 参数断点重连时，系统基于 `sequence_num` 自动从数据库补齐历史事件后再平滑接入实时广播；
  - 内置 20 秒周期性心跳机制防网关断联；
- **Flyway 数据库版本演进 (`V7__phase6_multi_agent_teams_and_message_bus.sql`)**：
  - 扩展 `teams` 表（topology, leader_agent_id, max_turns, config_json, status）；
  - 扩展 `team_members` 表（role_type, responsibilities, system_prompt_override, can_delegate）；
  - 扩展 `conversations` 表（team_id, last_sequence_num, summary, token_count, status）；
  - 扩展 `messages` 表（recipient_id, message_type, protocol_type, token_count, in_reply_to_id）；
  - 新增复合索引 `idx_messages_recipient`, `idx_messages_type_proto`, `idx_team_members_role`, `idx_conversations_team`，并预置 `team-dev-swarm` 经典开发团队种子基线数据；
- **全绿灯防御测试矩阵与架构守护**：
  - 新增 7 大测试套件：`TeamTopologyAndCoordinationTest`、`ConversationMonotonicSequenceAndConcurrencyTest`、`MessageRoutingAndVisibilityIsolationTest`、`CrossAgentProtocolAndLoopDetectionTest`、`ContextWindowAndRollingSummaryTest`、`ConversationSseStreamAndReconnectionTest` 与 `TeamAndConversationControllerIntegrationTest`；
  - 更新 `FlywayMigrationAndSchemaTest`，验证 V7 迁移后 19 张核心领域表；
  - 加固 Controller 安全鉴权与水平越权防御，更新 `ErrorCode` 规范定义 `4005-4010`；
  - 后端 187/187 项测试 100% 绿灯全通；前端 Next.js 14 生产构建 100% 成功。

---

## [v1.5.0] - 2026-10-08

### 🌟 阶段 5：Agent 统一接入层、多 Provider 与动态路由 (Phase 5 Deliverables)
- **Provider SPI 抽象与统一交互契约 (`LlmProvider` & Standard SPI)**：
  - 标准化统一定义统一消息模型 `ChatMessage` (role: system/user/assistant/tool, content, name)、请求模型 `ChatRequest` 与响应模型 `ChatResponse`；
  - 规范流式 Chunk 契约 `ChatChunk`，统一打通同步与 SSE 流式事件推送；
  - 细粒度测量每次调用的真实物理延迟（`latencyMs`）并准确统计 Token 消耗 (`prompt_tokens`, `completion_tokens`, `total_tokens`)；
- **多 Provider 生态适配矩阵 (Multi-Provider Ecosystem)**：
  - 落地 `OpenAiCompatibleProvider`（兼容 OpenAI 与 DeepSeek 标准 chat completions 协议）；
  - 落地 `AnthropicProvider`（Claude messages 协议，处理分层 system/messages 与 content_block_delta 流式协议）；
  - 落地 `GeminiProvider`（Google Gemini generateContent 与 streamGenerateContent REST 协议）；
  - 落地 `OllamaProvider`（本地 Ollama / vLLM 原生 `/api/chat` 零成本执行）；
  - 落地 `MockLlmProvider`（确定性模拟故障注入，支持 429 限流、5xx 服务端异常与延迟仿真）；
- **全链路凭证安全与敏感信息脱敏 (`SecretMasker`)**：
  - 支持 `env:VAR_NAME` 环境变量注入与 `prop:KEY` 配置解耦；
  - 严格防御全链路凭证泄漏：API Key 自动脱敏为 `sk-***` / `[REDACTED_SECRET]`，全面清洗日志、异常堆栈、请求头（`Authorization`, `x-api-key`）及 URL 查询参数（Gemini `?key=...`）；REST 接口与 DTO 绝不明文返回敏感密钥；
- **动态路由策略与高可用智能选择器 (`DynamicProviderRouter`)**：
  - 支持多维动态加权路由选择：按优先级 (`priority` 降序)、模型能力需求 (`capabilities`: code/general/fast/reasoning/long_context)、健康延迟 (`latencyMs` 升序) 与权重 (`weight`) 智能挑选最合适 Provider；
  - 提供路由决策预演端点 `POST /api/providers/route`，清晰返回主候选节点与备用节点链条；
- **高可用容灾与自动 Fallback 降级 (HA Failover & Circuit Breaking)**：
  - 落地线程安全熔断器 `CircuitBreaker`，管理 `CLOSED`, `OPEN`, `HALF_OPEN` 状态机流转；
  - 当主 Provider 发生 429 限流、5xx 超时、网络中断或连续失败达到阈值时，自动触发熔断并快速切换至备用 Backup Provider；
  - 自动向事件流和运行日志沉淀标准化降级告警事件 `FallbackEvent`（包含失败原因、状态码与接管节点）；
- **Token 与成本计量治理 (Token & Cost Accounting)**：
  - 落地 `ModelPricing` 定价核算引擎，内置主流大模型官方基准牌价（GPT-4o, DeepSeek, Claude 3.5 Sonnet, Gemini 1.5 Flash, Ollama 本地零成本），同时支持 Provider 实体自定义输入/输出百万 Token 计费覆盖；
  - 每次 LLM 调用精细化落库 `token_usages` 审计表，提供平台级总 Token 消耗与成本计量汇总端点；
- **Flyway 数据库演进 (`V6__phase5_agent_providers_and_routing.sql`)**：
  - 升级 `providers` 表（增加 priority, weight, capabilities, cost_per_million_input, cost_per_million_output, circuit_status, avg_latency_ms 等）；
  - 升级 `agent_definitions` 表（增加 preferred_provider_type, preferred_model, required_capabilities, fallback_enabled 等）；
  - 新增 `token_usages` 审计表并建立 `idx_token_usages_run`, `idx_token_usages_provider`, `idx_token_usages_created`, `idx_providers_status_priority` 高效索引；
  - 初始化系统级四大主流 Provider 及本地 Ollama 种子基线数据；
- **集成内核与 REST 端点**：
  - 落地 `ProviderDrivenAgentRuntime`，与 `ExecutionScheduler`、`DagExecutionEngine` 和 `AgentRuntimeRegistry` 深度集成；
  - 新增 REST 控制器 `ProviderController` (`/api/providers`) 与 `TokenUsageController` (`/api/token-usages`)；
- **全绿灯测试矩阵与运行时加固**：
  - 新增 7 大测试套件：`ProviderSpiAndPolymorphismContractTest`、`DynamicProviderRouterAndRoutingPolicyTest`、`ProviderFaultToleranceAndFallbackTest`、`TokenCostAccountingAndAuditingTest`、`SecretMaskingAndCredentialSecurityTest`、`ProviderAndTokenUsageControllerIntegrationTest` 与端到端 `ProviderDrivenAgentRuntimeIntegrationTest`；
  - 加固 `CircuitBreaker` 状态机：支持 `HALF_OPEN` 单探针并发隔离与失败即刻回退、真实物理延迟原子捕获与动态加权路由打分；
  - 加固多 Provider 消息模型防御：防范 null role 与 null content，杜绝 NPE；
  - 加固凭证安全：`SecretMasker` 扩展清洗 `x-api-key` 与 `x-goog-api-key` 请求头，Google Gemini 迁移至 Header 传参消灭 URL 泄密隐患；
  - 加固运行时外键约束：`TokenUsageApplicationService` 与 `ProviderDrivenAgentRuntime` 校验真实 providerId，彻底杜绝外键约束破损；
  - 更新 `FlywayMigrationAndSchemaTest` 验证 19 张核心领域表与 V6 脚本；
  - 后端 163/163 项测试 100% 绿灯全通；前端 Next.js 14 生产构建 100% 成功。

---

## [v1.4.0] - 2026-10-08


### 🌟 阶段 4：工作流编排引擎、DAG 依赖与数据流拓扑 (Phase 4 Deliverables)
- **版本化工作流 DSL 与 Kahn 拓扑排序校验 (`WorkflowDslValidator`)**：
  - 规范化定义 `WorkflowDsl`, `WorkflowNodeDsl`, `WorkflowEdgeDsl`, `RetryPolicyDsl` 等核心模型；
  - 基于 Kahn 算法实现确定性有向无环图 (DAG) 拓扑排序分层与成环检测（Cycle Detection），非法成环与自环检测严格抛出专用业务异常 `6001 WORKFLOW_INVALID`；
  - 覆盖孤岛节点孤立检测与入口节点/出口节点合法性严格校验；
- **真实并发调度与有界线程池 DAG 执行引擎 (`DagExecutionEngine`)**：
  - 调度引擎基于有界受控线程池调度兄弟节点真实并发执行，支持 `all_succeeded`、`any_succeeded` 以及自定义前置条件动态收敛；
  - 严格协同阶段 3 的底层状态机 `ExecutionStateMachine`，保障节点间状态转换原子性与细粒度锁安全性；
  - 优化全局快速响应中止与取消感知机制（`abortRemainingNodes` 与 100ms 快速中断轮询），彻底消除依赖中止与人工拒绝场景下的线程挂起与超时悬挂问题；
- **安全数据流管道与零 RCE 风险表达式计算 (`WorkflowExecutionContext` & `SafeExpressionEvaluator`)**：
  - 实现了线程安全的工作流上下文数据管线，支持动态参数插值与跨节点上下文字段提取（`{{steps.<nodeId>.outputs.<key>}}` 与 `{{inputs.<key>}}`）；
  - 彻底摈弃危险的 SpEL / 反射执行机制，自研递归下降安全表达式求值引擎，支持安全比较（`==`, `!=`, `>`, `<`, `>=`, `<=`）、逻辑组合（`&&`, `||`, `!`）与空安全解析，从根源杜绝远程代码执行 (RCE) 漏洞；
- **动态条件分支与级联跳过剪枝 (Dynamic Branching & Cascade Skip Pruning)**：
  - 智能评估边上的 `condition` 表达式，未命中分支对应的节点状态优雅收敛为 `SKIPPED`；
  - 下游依赖于未执行前置节点的后续分支自动触发级联跳过剪枝，避免任务悬挂与脏执行；
- **人工审批门禁 (Human-in-the-Loop) 与中断/恢复机制 (`ApprovalApplication`)**：
  - 支持 `requiresApproval: true` 的关键质量门禁与高危操作节点，执行至此时挂起当前 Step 与 Run 为 `WAITING_APPROVAL`；
  - 提供标准 REST 接口（`/api/approvals/{id}/approve` 与 `/api/approvals/{id}/reject`）及全流程事件审计；
  - 审批通过后无缝恢复后续 DAG 拓扑节点执行，审批驳回时优雅将 Step 标记为 `FAILED` 并级联熔断中止整个 Run，全程 SSE 实时直推；
- **Flyway 数据库演化 (`V5__phase4_workflow_dag_and_orchestration.sql`)**：
  - 新增与丰富 `workflow_definitions` (name, description, updated_at), `workflow_runs` (context_data_json), `step_runs` (inputs_json, outputs_json, requires_approval) 以及 `approvals` 表索引与审计字段；
- **全绿灯测试矩阵**：
  - 新增 6 大测试套件：`WorkflowDslValidationAndCycleDetectionTest`、`SafeExpressionEvaluatorAndDataFlowTest`、`WorkflowDagSchedulingAndParallelExecutionTest`、`WorkflowBranchingAndSkipPruningTest`、`HumanInTheLoopApprovalIntegrationTest` 与 `WorkflowDefinitionAndApprovalControllerIntegrationTest`；
  - 后端 127/127 项单元测试、集成测试与架构守卫规则 100% 绿灯通过；前端 Next.js 14 生产构建 100% 成功。

---

## [v1.3.0] - 2026-10-08

### 🌟 阶段 3：执行内核、状态机与 SSE 流式事件 (Phase 3 Deliverables)
- **严格 Run/Step 状态机 (`ExecutionStateMachine`)**：
  - 落地 `WorkflowRunStatus` 与 `StepRunStatus` 8 大核心生命周期状态（`PENDING`, `RUNNING`, `PAUSED`, `WAITING_APPROVAL`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `TIMED_OUT`）；
  - 严格定义并拦截非法状态跃迁（终态不可逆，非法跳转抛出专用错误码 `6007 INVALID_STATE_TRANSITION`）；
  - 完善步骤与工作流重试机制：支持 `FAILED` 与 `TIMED_OUT` 单步通过 `retryStep` 重置为 `PENDING` 并递增 `attempt` 次数，工作流重启为 `RUNNING` 时自动清空旧的终止态与超时时间戳；
  - 状态机流转事件全量委托 `RunEventBroadcaster` 统一发布，保障 `RUN_STATE_CHANGED` / `STEP_STATE_CHANGED` 实时直推 SSE，彻底消除了直接绕过广播器导致的序号失序与碰撞问题；
  - 基于 `runId` 的细粒度重入锁确保高并发竞态下的转移互斥，并在完成执行后提供安全的内存状态回收方法；
- **工作流取消与中断协作机制 (`CancelToken` & `CancelTokenRegistry`)**：
  - 实现非阻塞 `CancelToken` 信号机制，支持优雅终止回调注册；
  - 跨平台进程树递归强杀：结合 `ProcessHandle.descendants()` 递归回收与 Windows `taskkill /PID <pid> /T /F` 双层防御，彻底杜绝包装脚本下的孤儿进程残留；
  - 提供看门狗超时监控调度器，执行超时时自动触发强平看门狗并将 Run 收敛为 `TIMED_OUT`；
  - 级联取消所有待执行与活跃 Step，并持久化 `RUN_CANCELLED` / `RUN_TIMED_OUT` 事件；
- **真实执行解耦与 Runtime SPI (`AgentRuntime` & `ExecutionScheduler`)**：
  - 调度器 `ExecutionScheduler` 专注于节点拓扑编排、状态流转、重试策略、超时监控与事件沉淀，杜绝无界线程池 OOM 隐患，采用具名有界线程池及 `@PreDestroy` 优雅停机回收；
  - 抽象并落地 `AgentRuntime`、`ExecutionHandle`、`RuntimeDescriptor`、`RuntimeHealth` 与 `RuntimeEventSink` 标准接口；
  - 落地 `MockAgentRuntime`（测试与确定性离线模拟，明确标记 `simulated = true`）、`OpenAiCompatibleRuntime`（标准 HTTP 流式 API、推理内容容错与密钥脱敏）、`CliAgentRuntime`（本地子进程树与超时回收）；
  - 提供 `AgentRuntimeRegistry` 动态路由器，支持健康检查与降级处理；
- **实时 SSE 流式输出与 Last-Event-ID 断点重连 (`RunEventBroadcaster`)**：
  - 提供真实 SSE 流式输出端点 `GET /api/executions/runs/{runId}/stream` 与 `GET /api/runs/{runId}/stream`，彻底淘汰无状态长轮询假实时；
  - 每个事件具备单调递增 `sequenceId` 并持久化落库；
  - 完整实现 `Last-Event-ID` 请求头与 `lastEventId` 查询参数的断点续传重放，并增加 `PageRequest` 2000 条上限安全保护，防范海量事件回放 OOM；
  - `AuthFilter` 增强支持标准浏览器 `EventSource` 的 URL Token 查询参数认证；
  - 提供自动化 20 秒 `:heartbeat` 心跳保活机制，防止网络代理断开；
- **Flyway 数据库演进**：
  - 落地 `V4__phase3_execution_kernel_and_state_machine.sql`，为 `workflow_runs` 添加 `cancel_reason`, `cancelled_at`, `correlation_id`，为 `step_runs` 添加 `started_at`, `finished_at`, `duration_ms`, `correlation_id`，并建立状态查询高效索引；
- **全绿灯测试矩阵**：
  - 新增 `ExecutionStateMachineAndConcurrencyTest`、`WorkflowCancellationAndTimeoutTest`、`ExecutionSseStreamAndReconnectionTest` 与 `ExecutionSchedulerAndRuntimeIntegrationTest` 20 项集成测试用例；
  - 后端 99/99 项单元、架构守卫与集成测试 100% 绿灯通过；前端 Next.js 14 生产构建 100% 绿灯。

---

## [v1.2.1] - 2026-10-08

### 🐛 缺陷修复：跨平台 Linux/Windows 路径安全归一化与 CI 全绿修复
- **Linux CI 跨平台路径解析逃逸修复**：
  - 定位并修复 Ubuntu/Linux 下因反斜杠 `\` 不作为路径分隔符导致的目录遍历逃逸与 `.git` 拦截绕过问题；
  - 在 `DefaultWorkspaceResolver` 入口处强制收敛并归一化客户端路径（将 `\` 统一为 `/`），补全早期字符串切片 `.git` 与 NTFS 短名双层防护；
  - 增强跨平台 Windows 绝对驱动器盘符（如 `C:/...`）在 Linux 运行环境下的识别与拦截，确保统一抛出 `WORKSPACE_TRAVERSAL_DENIED (3004)`；
  - 修复 `WorkspaceLockManager.normalizeKey` 在 Linux 下因反斜杠未归一化导致的锁键不一致问题；
  - 远程 GitHub Actions CI 两个 Job（`Backend Tests & Build` 与 `Frontend Build & Typecheck`）全部成功通过（2/2 绿灯）。

---

## [v1.2.0] - 2026-10-08

### 🌟 阶段 2：安全工作区与 JGit 审计升级 (Phase 2 Deliverables)
- **深化受控工作区管理与多维沙箱防御**：
  - `WorkspaceResolver` 升级为基于 `workspaceId` + `relativePath` 受控解析，自动绑定数据库工作区或受控根目录；
  - 完善 `Path.normalize()`、`toRealPath()`、符号链接越权检测与根目录 Containment 校验；
  - 严格拦截设备文件（包含 Windows 保留字 `CON`, `PRN`, `AUX`, `NUL`, `CONIN$`, `CONOUT$`, `COM0-9`, `LPT0-9` 及其变种拓展名，专用错误码 3009）；
  - 严格拦截 `.git` 内部篡改、大小写变种（`.GIT`, `.Git`）、尾随点（`.git.`）及 Windows NTFS 8.3 短名称（`git~1` / `GIT~1`）；
  - `WorkspaceApplicationService` 严格接入跨租户归属权校验，全面防御 IDOR 水平越权；
- **统一受控工作区文件操作 API 与下载归档导出**：
  - 暴露标准 RESTful 控制器 `WorkspaceController`（`/api/workspaces/{workspaceId}/tree`, `/file`, `/file/rename`, `/file`, `/diff`, `/download`, `/archive`, `/lock/*`）；
  - 建立单文件 10MB 写入上限防御与 2MB 预览上限限制；
  - 增加二进制文件检测引擎（扩展名 + 首 4KB 空字节探测），禁止直接返回乱码，提供安全截断与结构化视图；
  - 提供单文件安全二进制流下载（`GET /{workspaceId}/download`）与整工作区排除 `.git` 的 ZIP 归档打包导出（`GET /{workspaceId}/archive`）；
- **JGit 审计与快照治理**：
  - 每次 Workflow Run 启动时自动建立 JGit 基线 commit（`Baseline commit for Run <runId>`）并打上基线 Tag；
  - 每个重要 Step 完成时自动创建快照 commit（`[StepSnapshot]`）与 Tag，精确统计变更文件清单并计算 SHA-256 校验和落库；
  - 修复 JGit Repository 在 try-with-resources 下 close() 双重调用的底层警告；
- **结构化 Diff 输出引擎**：
  - JGit 模块输出结构化 Diff 对象（`StructuredDiff` 与升级版 `FileDiffEntry`），涵盖文件变更类型（ADD/MODIFY/DELETE/RENAME/COPY）、增减行数、标准 Unified Diff patch、是否二进制、是否重命名、冲突标记（`hasConflict`）及审查状态；
- **工作区并发与租约治理 (Keyed Lock & DB Lease Sync)**：
  - 升级 `WorkspaceLockManager` 支持多 Owner 身份识别与租约超时（Lease TTL）模型；
  - 实现基于 `workspaceResolver` 的 Key 归一化，彻底消除 `workspaceId` 与物理文件路径之间的锁冲突不一致漏洞；
  - 落地与数据库 `workspace_locks` 租约表的持久化双写与跨实例协同，支持租约续期与超时防死锁自动回收（Deadlock Auto-Recovery）；
  - 引入锁释放时的无等待线程安全淘汰机制，彻底根除长期运行下的 `ConcurrentHashMap` 内存泄漏风险；
- **产物审查与安全非破坏性回滚**：
  - 实现 `ArtifactApplication` 与 `ArtifactController`，支持对 Step 产物执行 `accept`、`reject`、`revert`；
  - 回滚必须严格限定在受控 snapshot 基线内，禁止硬重置工作区（杜绝 `git reset --hard` 清空用户未提交工作的风险），仅将目标 Step 影响的文件还原至基线并产生审计 Revert Commit；
  - 支持快照元数据缺失时自动通过 Git Commit Diff 反向推导变更文件清单，并增加回滚路径越权与无效基线提交检查；
- **Flyway 数据库演进**：
  - 落地 `V3__phase2_workspace_audit_artifacts.sql`，为 `artifacts` 表补充 `review_status`, `reviewed_by`, `reviewed_at`, `review_comment` 审查字段与分布式锁租约表 `workspace_locks`；
- **全绿灯测试矩阵**：
  - 新增 `WorkspaceSecurityAndJGitAuditIntegrationTest` 19 项边界防御与深层功能测试用例（覆盖设备名拦截、NTFS 短名保护、下载与 ZIP 归档、数据库租约表协同、ID 与路径锁归一化、跨租户越权拦截、快照自动推导与安全回滚）；
  - 后端 79/79 项单元、架构守卫与集成测试 100% 绿灯通过；前端 Next.js 14 生产构建 100% 绿灯。

---

## [v1.1.0] - 2026-10-08

### 🌟 阶段 1：领域重构与持久化地基 (Phase 1 Deliverables)
- **九大核心业务领域划分与分层重构**：
  - 建立 `identity`, `project`, `agent`, `conversation`, `orchestration`, `execution`, `runtime`, `audit`, `sandbox`, `shared` 模块包结构；
  - 彻底解耦 Controller 与持久化 Repository，建立 Controller 仅依赖 `*Application` 契约体系；
  - 建立 Command / Query / View DTO 传输层规范，消除 JPA Entity 直接对外暴露；
- **Flyway 数据库版本演进与 18 张核心业务表迁移**：
  - 落地 `V1__init_schema.sql`，建立 `users`, `projects`, `workspaces`, `agent_definitions`, `agent_instances`, `providers`, `teams`, `team_members`, `conversations`, `conversation_participants`, `messages`, `workflow_definitions`, `workflow_runs`, `step_runs`, `run_events`, `artifacts`, `approvals`, `deployments` 共 18 张核心业务表结构；
  - 优化核心联合索引（`idx_messages_conv_seq`, `idx_run_events_run_seq`, `idx_wf_runs_idemp`），兼顾 H2 (MySQL Mode) 与 MySQL 8.0 双重适配；
  - 落地 `V2__seed_system_baseline.sql` 初始系统基线数据；
  - 会话参会人淘汰逗号拼接，重构为 `conversation_participants` 关系表；消息流增加单调递增 `sequence_num` 与 `schema_version = "v1"` 结构定义；
- **认证鉴权、水平越权防御与链路治理**：
  - 实现基于 Token 认证体系（包含 HMAC-SHA256 签名、随机 Nonce 防碰撞与登出黑名单机制）及 BCrypt 强哈希密码校验；
  - 提供完整认证生命周期接口：`register`, `login`, `refresh`, `logout`, `me`；
  - 彻底剔除未认证请求隐式降级至 `user-1` 的安全旁路漏洞，严格要求所有受控接口携带有效身份令牌；
  - 新增 `ResourceAccessGuard`，实施跨用户/跨租户资源归属强制鉴权（403 `FORBIDDEN`）；
  - 全链路集成 `RequestCorrelationFilter`，自动透传 `X-Request-ID` 与 `X-Correlation-ID`，并在统一响应体 `Result<T>` 中返回 `requestId`；
  - 全局配置 `spring.jpa.open-in-view: false`，规范事务边界；
- **执行引擎 RESTful 控制器与事件回放**：
  - 新增 `ExecutionController`，对外暴露运行启动（幂等防重）、状态查询、步骤获取与事件断点续传（`afterSeq` 游标）；
  - 执行引擎全链路校验目标项目的归属权，杜绝跨租户执行注入；
- **自动化架构守卫与 60 项防御性测试矩阵**：
  - 引入 ArchUnit 架构自动化守护 (`ArchUnitArchitectureTest`)，强制约束 Controller 与 Repository 的调用隔离、分层归属以及严禁直接泄露 JPA 实体对象；
  - 修复 `GlobalExceptionHandler` 对领域业务异常 `BusinessException` 的捕获穿透缺陷，统一错误码字典；
  - 新增 `RestApiSecurityAndMultiTenancyIntegrationTest`，对注册/登录/刷新/注销、无凭证访问拒绝、跨用户 Project/Workspace/IM 会话越权拦截进行真实 MockMvc HTTP 端到端检验；
  - 后端 60/60 项单元、集成与防御性测试矩阵 100% 绿灯通过；前端 Next.js 14 生产构建通过。

---

## [v1.0.0] - 2026-10-06

### 🌟 核心特性
- **企业级 DDD 四层架构体系**：完成适配层、应用层、领域层与基础设施层的四层解耦，搭建高内聚低耦合的 Spring Boot 3.3.4 后端工程基石；
- **统一智能体 SPI 插件引擎 (UnifiedAgentAdapter)**：抽象多运行时统一接入契约，打通 Claude Code、Codex CLI、OpenClaw 与 DeepSeek V3 API 的双向流式通信；
- **飞书级群聊 IM 协作总线**：支持单聊、多会话并发管理与 `@Mention` 智能指令路由；集成 Orchestrator 任务拓扑拆解引擎与富文本交互卡片（Interactive Cards）；
- **原生 JGit 工作区版本审计**：基于 Eclipse JGit 内核自研行级增删 Unified Diff 计算引擎，精准捕获多 Agent 编写代码的每一处变动；
- **现代化 Bento Grid 协作控制台**：基于 Next.js 14 + Tailwind CSS 实现现代化暗黑美学前端大厅，内置可视化 DAG 工作流拖拽画布、物理工作区文件树与代码编辑器、实时 Web 预览沙箱与 Docker 一键容器化部署。

### 🛡️ 安全与工程规范
- **全方位凭证脱敏与分层隔离**：全面移除代码库内任何硬编码密钥，改用环境变量与本地安全文件隔离方案；
- **全量单元与边界防御测试**：13/13 项单元测试与集成测试矩阵全部 100% 绿灯通过；
- **AOCI (AI-Oriented Code Indexing) 架构代码地图**：在根目录建立符号-语义高密度索引标准，杜绝长程研发中的架构遗忘。
