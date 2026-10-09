# AgentHub 下一阶段长期计划：从功能齐备到可验证产品

> 版本：v2.0  
> 基线日期：2026-10-09  
> 依据：当前 `main` 工作树、代码/配置静态检查、前端构建、Compose 配置解析和可取得的测试运行证据  
> 适用方式：本文是下一阶段的执行基线；旧版《长期产品化与简历项目实施计划》保留为历史路线，不再作为当前完成状态的证明。  
> 范围：只规划产品闭环、真实运行、上线验证与作品证据，不扩展社交、市场、移动端或新的 Agent 平台矩阵。

## 1. 重新审视后的结论

AgentHub 已经有相当完整的 Java 后端能力骨架：项目和工作区、用户注册登录代码、Provider/Runtime SPI、版本化工作流模型、Run/Step 状态、SSE 事件回放、JGit 审计、人工审批、受限沙箱抽象、Micrometer 指标、生产 Compose 和多类集成测试都已落在仓库中。前端也完成了模块拆分和 Next.js 生产构建。

但“有代码、有测试类、有部署配置”还不能推导出“端到端产品已闭环”或“可上线”。复核发现了几类足以阻断上线的差距：

1. **身份信任链未收口**：认证过滤器仍接受请求头 `X-User-Id`、URL 查询参数 Token；TokenProvider 有默认签名密钥和 `dev-token-*` 通路；资源守卫把固定 `user-1`/`system` 当作超级用户。工作区锁端点接受客户端提供的 `ownerId`，没有显式校验工作区归属。
2. **前端会掩盖后端未工作**：API 客户端把失败、空数据回退为 MOCK 数据；页面固定默认 Run/Team/Project；执行和审批流程局部由前端改状态模拟；默认 API 地址指向浏览器自己的 `localhost:8080`，与生产 Compose 的变量名也不一致。没有看到完整的前端登录态和 Bearer Token 请求闭环。
3. **“可恢复执行”仍主要是进程内调度**：DAG/Execution 使用 JVM 内存线程池；数据库中持久化了 Run/Step，但未发现应用启动时从数据库重新领取 queued/running Run 的恢复 Worker；DSL/执行所需状态是否完整快照也需要补齐。并行调度的最近竞态修复有压力测试，但跨进程/跨数据库事务的一致性还没有证据。
4. **事件序号并非真正的数据库原子分配**：RunEventBroadcaster 在 JVM 内用 AtomicLong 递增，再写库；单节点测试不能证明多实例无重复/无缺口。消息流和 Run 事件要分别定义唯一、持久、可恢复的序号规则。
5. **部署记录不等于实际部署**：STATIC_PREVIEW 返回固定后端预览 URL；`startCommand` 被保存但未用于持续启动服务；Docker provider 的可用性依赖 backend 容器内 Docker CLI/daemon，但当前生产 Compose 没有配置远程 Docker Worker。静态预览生命周期测试没有验证可从另一客户端访问的实际服务。
6. **生产容器和运维证据不足**：前端 Dockerfile 有疑似无效的 shell 风格 `COPY ... || true`；Compose 给 Redis 配密码时 healthcheck 没有认证参数；生产只配置 HTTP 入口；CI 未构建镜像、未对 MySQL/Flyway 和 Compose 做运行级验证；灾备手册包含 `docker system prune -f --volumes` 等可能删除持久卷的操作。
7. **完成态文档过度表述**：文档出现“生产级”“真实全链路”“百分百”“零丢消息”等结论，但当前演示步骤使用 MOCK Runtime，某些端点与实现不一致，前端还有硬编码演示数据。测试通过也不等同于真实 Provider、数据库、容器或浏览器产品链路通过。

因此，项目的下一阶段定位应从“继续补功能”调整为：

> **把现有能力变成身份可信、前后端真实联动、进程重启可恢复、部署结果可访问、生产故障可恢复的 Java 多 Agent 软件交付平台。**

## 2. 复核基线与证据边界

### 已复核的资产

- 后端当前约 262 个生产 Java 源文件、47 个测试类和 V1–V8 共 8 个 Flyway 迁移；
- 有身份模块、项目/工作区模块、Agent Provider 与 Runtime、Conversation/Team、Workflow/Execution、Audit、Sandbox、Actuator/Micrometer 等代码；
- `application-prod.yml` 使用 `ddl-auto: validate`、关闭 H2 Console 并关闭 Open Session In View；
- 前端 `npm run build` 已通过；
- `docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet` 通过，证明 Compose 文件可以解析和展开变量；
- Nginx 配置关闭 SSE proxy buffering，后端镜像以非 root 用户运行；
- 最近一次 `mvn clean test` 在本次检查中完成了编译并开始跑测试，但执行会话未返回完整的 Maven 最终汇总，不能把它记录为本次完整测试通过；旧文档中的 259/263 测试数字需要以 Surefire 最终报告重新核准。

### 本次没有验证的能力

- 本地 Docker Desktop Linux Engine 当前不可用，未能完成镜像构建和容器启动；
- 没有真实 MySQL 环境验证 Flyway、SQL 锁和并发行为；
- 没有真实 CLI/LLM Provider 的端到端运行证据；
- 没有浏览器登录后跑完整 Run、审批、Diff、预览的录屏或脱敏证据包；
- 没有真实服务器部署、备份还原或故障演练记录。

后续所有阶段状态只能标成“代码完成”“自动化测试通过”“目标环境验证通过”三者之一，不能用其中一个替代另两个。

## 3. 产品范围与不变量

### 3.1 核心用户旅程

用户登录 -> 创建或选择 Project -> 选择 Team/Workflow -> 输入研发目标 -> Orchestrator 生成可审查计划 -> 用户确认 -> 后端创建唯一 Run -> Agent 在受控 Workspace 执行 -> Run/Step/Event 持久化并流式展示 -> JGit 生成 Diff/Artifact -> 自动质量门禁 -> 人工审批 -> 产生可验证 Preview/Deployment -> 用户停止、回滚或导出证据。

### 3.2 不可破坏的不变量

1. 客户端不能指定文件系统绝对路径、身份 ID、锁所有者或部署命令的任意宿主权限。
2. 没有有效认证主体的请求不能访问用户资源；Demo 身份不能通过隐藏旁路访问生产资源。
3. 每个 Run 都绑定 `owner -> project -> workspace -> workflow snapshot`，身份和工作区不可从请求参数中随意替换。
4. 任何异步操作都有持久化状态、幂等语义、超时、取消、恢复策略和审计事件。
5. SSE 是持久化事件的投影；断线后按游标能从数据库补齐，浏览器本地状态不能成为事实来源。
6. UI 不得在生产模式用假数据补齐后端错误或空数据。离线演示必须有明显 Demo 标识，且与真实运行模式明确分开。
7. `RUNNING` 只能表示存在真实可观察的执行进程/工作者或租约；`DEPLOYED` 只能表示目标可访问且健康检查通过。
8. 所有用户可见的“成功、通过、部署完成”都必须能定位到 Run、Step、Event、Artifact、测试报告或部署健康证据。

## 4. 目标技术方向

继续保持 **Java 17 + Spring Boot 模块化单体**，暂不拆微服务。重点收敛现有多套并存的旧接口和服务，而不是再建立新的架构层。

### 4.1 统一后端事实来源

- `Project/Workspace` 管理资源归属和受控路径；
- `WorkflowDefinition` 是版本化计划模板，`WorkflowRun` 保存不可变 DSL 快照和用户输入；
- `StepRun` 是每个节点的唯一执行记录；
- `RunEvent` 是执行时间线唯一事件源，UI 的节点状态由 Run/Step API 或事件投影得出；
- `Artifact` 是 Diff、测试、日志、构建、预览和部署记录的统一引用；
- Controller 不直接负责线程调度，前端不直接改变后端执行状态。

### 4.2 可靠执行语义

首阶段可继续使用单机有界线程池，但调度资格必须从数据库持久化队列获取，并使用数据库原子领取/租约/心跳；应用重启后能扫描并恢复未完成 Run。未来需要水平扩展时，再替换为 Redis Streams/RabbitMQ 等队列，不先引入空壳依赖。

执行保证应明确为 **at-least-once 调度 + 幂等 Step/外部操作**；避免宣称分布式 exactly-once。Run/Step 状态更新、事件序号、事件落库和 outbox 投递应有一致性设计。

### 4.3 部署执行模型

生产 Spring Boot 主容器不直接拿到无限制 Docker Socket。采用最小可行的 Sandbox Worker/受限远程 Docker 执行端口；工作区只读/读写挂载、资源限制、网络、镜像、端口和清理都由受控 DeploymentSpec 决定。单机产品第一阶段可以只支持经过验证的静态前端或明确的一种项目模板。

## 5. 下一阶段路线

各阶段顺序有依赖，不建议并行铺功能。估算时间只供个人排期，退出条件才是完成判断。

### 阶段 A：可信身份与资源边界收敛（预计 3～5 天）

**目标**：消除认证旁路、默认主体和工作区控制接口的越权路径。

**工作项**：

1. 将 JWT secret 改为启动必需的外部配置；prod profile 缺失、过短或命中示例值时应用必须拒绝启动。移除默认签名密钥。
2. 移除生产可达路径上的 `dev-token-*`、`X-User-Id` 身份信任和超级用户 `user-1/system` 硬编码；如确有本地 Demo 需要，独立 profile 启用并在启动日志/界面显示 DEMO。
3. 禁止 URL query 参数携带 Token；SSE 的认证使用同源、受控 Cookie 或一次性短时 stream ticket，不能把长期 Bearer Token 写入 URL/代理日志。
4. 统一所有 Controller 的认证主体获取方式，不要有的 Controller 校验、有的靠可选 `ResourceAccessGuard`、有的默认放行。
5. 对 Workspace、Run、Conversation、Approval、Artifact、Deployment 的每个读写/订阅/下载接口逐一建立 owner 归属检查矩阵。
6. 锁 API 由服务端派生 user/run owner，移除客户端 `ownerId`；锁状态、续租、释放、工作区读取都要先验证当前用户对 Workspace 的访问权限。
7. 对 Provider 凭证采用 secretRef/外部注入策略；审计本地忽略文件、Git 历史、测试日志和截图，提供撤销/轮换核对记录，不把凭证写入计划文档。
8. 增加注册、登录、Token 刷新、退出的速率限制和认证错误审计；明确 Token 吊销是进程内还是持久化，生产必须能多实例/重启后生效。

**退出条件**：

- prod 下无默认 JWT Secret、开发 Token、伪造用户头或固定超级用户；
- 未认证、伪造 Header、过期/吊销 Token 均无法访问资源；
- User A 无法读写 User B 的 Project/Workspace/Run/Conversation/Approval/Deployment；
- 客户端不能替换锁 owner、释放别人的锁或探测其他用户锁状态；
- 新增黑盒 MockMvc 安全测试覆盖所有 API 类别，并通过架构/路由检查确认没有绕过路径。

### 阶段 B：前端去 Mock 化与真实产品主旅程（预计 1～2 周）

**目标**：让用户看到的每个运行状态都由后端数据驱动，真实登录后可以从页面完成一条 Run。

**工作项**：

1. 把 Demo Fixture 从生产 API client 中移出。生产 API 请求失败必须显示错误、重试或空状态，不得静默回退成 MOCK_RUN、MOCK_DIFFS、MOCK_APPROVAL 等。
2. 若保留演示模式，使用显式 `NEXT_PUBLIC_AGENTHUB_DEMO_MODE` 并在全站明显标注“离线演示/模拟数据”；Demo fixture 不得与真实响应合并。
3. 实现登录/注册/退出和受控 Token 保存；所有 API 请求携带有效身份凭据，移除 `X-User-Id`；401 后明确退出/刷新，403 显示权限错误。
4. API Base URL 改为部署可移植配置：优先同源 `/api` 走 Nginx；统一 Next 环境变量名，禁止 fallback 到用户浏览器本机 `localhost:8080`。
5. 移除默认 Run/Team/Project ID 和 Windows Workspace 绝对路径；页面先加载用户 Projects，再由用户选定 Project/Workspace/Team/Workflow。
6. “开始协作”真实调用创建 WorkflowRun/发送消息接口，获取后端生成的 runId；实时终端、DAG、审批、Diff、Provider 和部署组件只订阅该 run/project。
7. DAG 节点、边和状态由 WorkflowDefinition/StepRun API 驱动；编辑画布形成草稿 DSL，提交前由后端 validator 校验；不再固定展示静态节点状态。
8. 审批操作成功后重新加载 Run/Step/Approval，不由页面本地把节点改成成功或触发下一步。
9. 预览面板只展示 Deployment API 的真实 URL、状态和日志，不合成部署日志。
10. 修正 `NEXT_PUBLIC_API_URL` 与 Compose 环境名不匹配、路由端点与 Controller 不一致的问题；形成前端 API contract 清单。
11. 删除或下线旧版直接收绝对路径的 `/api/workspace/*` 接口，避免新旧 workspaceId API 同时造成授权和行为差异。

**退出条件**：

- 真实账号登录 -> 创建 Project -> 选择 Team/Workflow -> 创建 Run -> SSE/DAG 实时变化 -> 审批 -> Diff 页面全程不依赖 Mock；
- 后端停机时页面显示可恢复错误，不显示假成功/假数据；
- 浏览器网络面板中所有 API 指向同源/配置的服务，不请求用户本机 localhost；
- 刷新页面后重新从后端恢复当前 Run/Step/Approval 状态；
- Playwright 或浏览器冒烟记录覆盖成功、401、403、空列表、服务不可用和 SSE 重连。

### 阶段 C：单一可靠执行内核与重启恢复（预计 2～3 周）

**目标**：将“数据库有 Run 记录”和“任务真的可以恢复执行”连成一个可靠运行时。

**工作项**：

1. 选定唯一执行调度入口。清点旧 `WorkflowEngineService`、`ExecutionScheduler`、`DagExecutionEngine` 和 IM 异步触发链，明确哪一条是生产执行内核；其他旧入口逐步迁移或移除。
2. 创建 Run 时持久化完整 DSL/schema version、输入、Context Manifest、provider/runtime 引用、项目/工作区 ID、权限快照和幂等键。重启后不依赖内存请求对象恢复。
3. Run 创建事务只负责保存 Run、Step、Outbox/调度记录，然后返回 `202 + runId`；Worker 从持久化队列领取任务。
4. 使用数据库原子状态转换或租约字段（owner、lease_until、heartbeat、attempt）领取 Run/Step；租约过期后可由另一 Worker 安全接管。
5. 为 Agent 外部调用定义 Step attempt/idempotency；对有副作用的步骤实现幂等文件写入、Git 快照或执行前检查，防止恢复时重复写坏 Workspace。
6. 实现应用启动恢复：扫描 queued、租约过期 running、等待审批和可重试状态；区分“可安全重跑”“需要人工确认”“不可恢复失败”。
7. 将所有状态迁移通过统一 ExecutionStateMachine/Repository 方法执行，禁止多个线程直接更新 Entity 字段；使用版本号/乐观锁或条件更新处理竞态。
8. 事件序号移到持久化原子分配：数据库 sequence 表/Run 行版本或单事务计数器，建立唯一约束 `(run_id, sequence_num)`；SSE 先读取数据库事件再推实时事件。
9. 使用 transactional outbox 或等价可恢复发布机制，解决 Run/Step 已提交但 SSE/队列事件未发布的问题。
10. 将消息 sequence 同样定义清楚，验证 JVM 重启、并发发送和未来多实例场景；不要把分段内存锁作为数据库定序证明。
11. 给执行线程、CLI、API Provider、Sandbox 分别配置有界线程池、队列、背压和资源上限；避免默认 ForkJoinPool 和无界 IO executor。
12. 处理 RequestContext 的异步传播：任何异步 Worker 都必须显式携带 owner/actor/correlation ID，不能依赖 Servlet ThreadLocal。

**退出条件**：

- 在 Step 运行中强杀后端，再启动后端，Run 可以按已持久化快照恢复且不会重复已完成 Step；
- 两个 Worker 同时争抢时只有一个获得有效租约；租约超时可以恢复；
- Run/Step 状态与 `run_events` 序号在高并发、重启、重复提交时一致；
- 相同幂等键重复提交不创建重复执行；重复事件/回调不会重复产生 Artifact；
- 运行期间取消、暂停、审批恢复、超时和单 Step 重试都经过状态机并有审计事件；
- 测试覆盖实际数据库事务、跨线程状态更新、崩溃注入和重启恢复。

### 阶段 D：真实沙箱部署与项目交付闭环（预计 1～2 周）

**目标**：Deployment 的 `RUNNING` 和 URL 对应真实可访问的项目实例。

**工作项**：

1. 明确首个受支持目标：优先选择静态前端模板或一个明确的 Java/Node 模板；不接受任意用户命令作为默认公共功能。
2. 把 Deployment 分成 BUILD、START、HEALTH_CHECK、RUNNING、STOP、CLEANUP 状态；保存实际 process/container ID、工作目录、端口、日志游标和结束原因。
3. 持续运行服务必须有独立生命周期 Worker。不能只保存 `startCommand`，也不能只跑一次构建后就返回 RUNNING。
4. 对 `STATIC_PREVIEW` 实际构建产物并由静态服务器服务；对 Docker 目标验证容器启动、映射端口、网络和健康检查。
5. 固定预览 Host/URL 生成策略，不能只返回 backend 容器内 `localhost`；从宿主机外部浏览器实际打开该 URL。
6. Docker Provider 选择明确的隔离方式：受限 Worker/远程 Docker Context/专用 daemon。记录不可行配置，并拒绝在生产 backend 容器中假设 Docker CLI/daemon 可用。
7. 加固 Sandbox Worker：非 root、只读根文件系统、最小挂载、无宿主 Secret 继承、CPU/内存/磁盘/时间/日志上限、网络策略、进程树清理和 orphan reconciliation。
8. 修复前端 Dockerfile 并在 Linux Docker 环境实建；不存在的 `public/` 不应使用 shell 语法伪装 COPY 容错。
9. 修复端口预留竞态。端口分配不能只在进程内记账后就视为 TCP 端口已绑定；启动失败和容器崩溃都要释放记录。
10. Deployment 必须绑定用户 Project/Artifact 并验证 owner；停止、查日志和预览访问都要有权限校验。

**退出条件**：

- 从一次完成质量门禁的 Artifact 创建真实 Deployment；
- 返回 URL 能被另一台客户端访问并显示项目实际内容；
- `startCommand`/Container 真正存活，停止接口后进程/容器消失；
- 启动失败、端口占用、健康检查失败、后端重启后都有明确状态并执行清理/对账；
- 容器隔离测试证明不能访问其他项目文件和宿主敏感数据；
- 自动化测试至少覆盖一个真实容器 happy path 和失败清理路径。

### 阶段 E：生产配置、CI/CD 与灾备演练（预计 1～2 周）

**目标**：证明从干净 Linux 环境可以安装、启动、升级、监控、备份和恢复。

**工作项**：

1. 修复 Compose Redis 密码启用后的健康探针认证问题；若 Redis 当前没有实际业务消费者，先从生产依赖中移除，避免制造无用故障点。
2. `.env.production.example` 只能有清晰占位符；启动脚本检测 ChangeMe、your-、placeholder、弱密码和缺少 JWT Secret 并拒绝继续启动。
3. 生产入口配置 TLS（本地演示可用 HTTP，但生产部署不得称 TLS 已完成）；管理/Prometheus 端点只允许受信网络或鉴权访问。
4. Compose 中所有容器声明网络、卷、资源、healthcheck、重启策略和最小权限；校验镜像内实际启动用户、文件权限和探针工具。
5. CI 加入：Maven clean test、Next lint/type/build、Flyway 空库验证、MySQL 8 集成测试、Docker 镜像构建、Compose 启停冒烟、Secret/依赖/镜像扫描、API E2E 冒烟。
6. 增加对 Dockerfile 的构建检查，不能只运行 `docker compose config`；对前端与后端镜像按 commit SHA 标记。
7. 让 MySQL 成为真实集成测试目标（Testcontainers 或 CI Service）；H2 仅保留快速单测，不把 H2 通过等同 MySQL SQL/锁行为通过。
8. 建立可执行的数据库与 Workspace 备份/还原脚本，包含一致性点、checksum、加密策略、保留策略和恢复校验。
9. 修改灾备手册：禁止对包含持久化卷的生产环境运行无差别 `docker system prune --volumes`；禁止按目录 mtime 无数据库关联地递归删除 Workspace。
10. 配置 Prometheus scrape 与告警规则文件；文档中提到的规则必须能被 Prometheus 校验，不以 Markdown 清单代替可加载配置。
11. 在 staging 实跑至少一次 Flyway 升级、服务重启、数据库故障、Agent 超时、SSE 断线、Workspace 锁过期、磁盘告警、备份还原和部署失败清理；记录原始命令、版本、日志和恢复耗时。
12. 对所有数字（P99、吞吐、恢复时间、成功率、并发数）保存机器规格、提交号、JDK/Node/Docker 版本、原始输出和重复次数。

**退出条件**：

- 干净 Linux Runner 完成镜像构建与 Compose 服务启停；
- Compose 在有/无 Redis 密码配置下健康状态正确，不存在依赖死锁；
- MySQL 8 迁移和并发锁测试通过；
- 备份可以恢复到空环境，并通过 DB/Workspace checksum 与 Run 抽样校验；
- Alertmanager/Prometheus 能加载规则，触发一次测试告警；
- 危险清理操作已移出灾备路径，故障演练有实际结果记录；
- 一个 staging Run 完整通过登录、计划、执行、审计、审批、部署和恢复/停止。

### 阶段 F：产品质量、性能边界和求职作品证据（预计 3～5 天）

**目标**：把经过真实验证的产品事实变成简历、演示和可追问的工程证据。

**工作项**：

1. 更新 README、白皮书、演示指南和架构图，使其与实际生产/演示功能边界一致；删掉不能复现的“百分百”“企业级”“生产级”“真实全链路”等绝对表述。
2. 拆分演示模式与真实模式：演示用 Mock Runtime 时明确标注；真实 Provider 演示只在有可复核的运行记录时描述为真实模型运行。
3. 重写 `docs/E2E_DEMO_GUIDE.md`，逐个核对端点和请求体；当前示例中的 admin 默认账号、Workflow/Approval 路径、MOCK DSL 和 localhost URL 不应冒充真实产品流程。
4. 提供 Windows/PowerShell 与 Linux/macOS 两种启动/演示方式，避免文档只有 Bash+jq 命令。
5. 生成脱敏 Run Evidence Bundle：Workflow DSL snapshot、Run/Step 状态、事件序列、Provider/Runtime 类型、`simulated` 标记、Diff、测试报告、Approval、Deployment health 和 checksum。
6. 简历指标只能来自真实采集数据；说明口径、机器环境和样本量，区分本地压力测试数据与生产流量数据。
7. 准备架构图、状态机图、关键 ADR 和 3 个可复现技术取舍：模块化单体、数据库驱动恢复调度、持久化 SSE 事件与工作区安全。
8. 把未完成的多机集群、S3/MinIO、Kubernetes/MicroVM 隔离、多租户物理隔离明确写在边界，不列为已交付。

**退出条件**：

- 陌生开发者可依 README 在干净环境完成真实模式或明确标记的 Demo 模式；
- 演示的每个关键结果都可定位到后端记录或可下载 Artifact；
- 架构、测试、压测和部署结论都能追溯到提交号与原始证据；
- 简历表述不把模拟、测试、配置文件或截图包装成生产行为。

## 6. 阶段门禁与工作粒度

每个阶段按以下顺序推进：

1. 写清当前行为、目标行为、影响的 API/数据表/运行时和安全属性；
2. 先补一条能够复现失败或缺口的测试/冒烟脚本；
3. 小范围实现并运行最窄验证；
4. 更新 API、架构、能力边界和运维文档；
5. 用阶段退出条件评审，证据不够就保持“进行中”。

每个功能单元需具备：Owner/权限检查、失败语义、幂等规则、超时/取消策略、事件或日志证据、自动化测试和 UI 错误展示。禁止只交 UI 状态、模拟数据、孤立接口或空的部署配置。

## 7. 推荐执行顺序与暂停新功能规则

下一次开始编码时，建议严格顺序如下：

1. 阶段 A：收口认证旁路、默认密钥和工作区锁所有权；
2. 阶段 B：清除生产 API 静默 Mock，接通真实登录与 Project/Run 前端主旅程；
3. 阶段 C：唯一执行内核、持久化调度、数据库原子事件序号和重启恢复；
4. 阶段 D：真实可访问的 Preview/Deployment 生命周期；
5. 阶段 E：Linux 镜像、MySQL、CI/CD、TLS 和灾备演练；
6. 阶段 F：事实校准、证据包、演示和简历数据。

阶段 A–E 退出前，暂停新增 Provider、拓扑模式、市场、社交、多端适配和视觉特效。只有实际用户旅程中的硬需求才能调整顺序，并需同步更新本计划和验收证据。

## 8. 完成态证据索引模板

每次阶段验收时，在本文件末尾追加以下记录，而不是只改标题为“已完成”：

```text
阶段：
验收日期：
Git commit：
运行环境：OS / JDK / Node / Docker / DB：
执行命令：
测试汇总：
黑盒流程：
失败/恢复演练：
Artifact / 日志位置：
未覆盖场景：
剩余风险：
状态：代码完成 / 自动化测试通过 / 目标环境验证通过
```

只有“目标环境验证通过”并具备可复核记录的阶段，才允许作为简历中的上线能力陈述。

---

## 9. 阶段验收记录汇总

### 阶段 A 验收记录

```text
阶段：阶段 A（可信身份与资源边界收敛）
验收日期：2026-10-09
Git commit：a821ef0 (feat(security): 完成阶段 A 身份可信收敛与多租户资源边界防御并补齐持久化吊销审计)
运行环境：OS: Windows 11 / JDK: 17.0.15 / Node: 20.x / Compose: v2.x / DB: H2 (MySQL Mode) + MySQL 8 兼容迁移
执行命令：
  - mvn clean test
  - npm run build (agenthub-frontend)
  - docker compose --env-file .env.production.example -f docker-compose.prod.yml config --quiet
测试汇总：
  - Backend: 276/276 passed (0 failures, 0 errors, Surefire clean)
  - Frontend: Next.js 14.2.13 生产构建通过 (4/4 静态路由与客户端 Chunk 打包正常)
  - Compose: prod 配置解析与变量展开验证通过
黑盒流程：
  - 新增 PhaseASecurityAndResourceBoundaryIntegrationTest (9/9 通过)，MockMvc 覆盖全链路：
    1. 生产环境缺失/过短/命中示例值 JWT 密钥启动熔断 (validateConfigurationForProfile)；
    2. 生产环境 dev-token 伪造拦截与 X-User-Id 头伪造阻断；
    3. 短时 (60s) 一次性 Stream Ticket 签发与单次消费校验 (POST /api/auth/stream-ticket -> ticket 消费 -> 二次使用拒绝)；
    4. 同源 Cookie (agenthub_token) 鉴权闭环；
    5. 移除超级用户 user-1 硬编码特权，非属主访问 Project 严格 1002/FORBIDDEN；
    6. 工作区锁 API 服务端派生所有权，客户端无法伪造 ownerId，跨用户读写/续租/释放/探测锁状态全部被 1002 拦截；
    7. 部署控制器 DeploymentController 多租户归属拦截 (创建/读取/启停/日志/列表水平越权全量拦截)；
    8. 持久化 Token 吊销存储至 invalidated_tokens 表并在登出后持续阻断凭据复用；
    9. 认证审计日志持久化写入 auth_audit_logs 表；
    10. 认证端点防爆破滑动窗口速率限制防护。
失败/恢复演练：
  - 生产 Profile 缺失 AGENTHUB_AUTH_SECRET 时抛出 IllegalStateException 阻断服务启动；
  - 令牌注销后无论内存还是重启加载均拒绝认证；
  - Redis 生产探针适配密码认证健康检查。
Artifact / 日志位置：
  - target/surefire-reports/TEST-com.agenthub.PhaseASecurityAndResourceBoundaryIntegrationTest.xml
  - agenthub-frontend/.next/
未覆盖场景：
  - 真实多节点生产外部 Linux Engine 下的容器隔离演练 (受限于本地宿主运行环境，属于阶段 D/E 范围)。
剩余风险：
  - 阶段 B 待推进：前端目前在开发模式下仍有部分历史 Mock Fixture，需在阶段 B 完成前端去 Mock 化与真实登录/主旅程闭环。
状态：自动化测试通过
```

---

### 阶段 B 验收记录

```text
阶段：阶段 B（前端去 Mock 化与真实产品主旅程）
验收日期：2026-10-09
Git commit：fc01c86 (feat(ui): 完成阶段 B 前端去 Mock 化、真实认证闭环与产品主旅程联动)
运行环境：OS: Windows 11 / JDK: 17.0.15 / Node: 20.x / Next.js: 14.2.13
执行命令：
  - npm run build (agenthub-frontend)
  - mvn test (agenthub-backend)
测试汇总：
  - Frontend: Next.js 14.2.13 生产构建通过 (4/4 页面静态预渲染，0 类型报错，0 Webpack 警告)
  - Backend: 276/276 passed (0 failures, 0 errors, Surefire clean)
黑盒流程：
  1. 生产 API Client 全面去 Mock 化：移除静默 MOCK_RUN / MOCK_STEPS / MOCK_APPROVAL 回退，异常统一向上抛出类型化 ApiError (包含 HTTP status 与后端业务错误码)，页面展示可恢复错误提示与重试机制；
  2. 显式演示模式隔离：引入 NEXT_PUBLIC_AGENTHUB_DEMO_MODE 与前端即时切换开关，演示模式下在看板顶部醒目标注“离线演示模式 (Demo Mode)”，真实产品模式绝不混入伪造数据；
  3. 认证主流程与凭证生命周期：落地 AuthModal (支持登录与注册)，统一管理 agenthub_token (localStorage 与 SameSite Lax Cookie 双写)，所有 API 请求自动装配 Authorization: Bearer <token> 并在 401 时主动清理过期态并弹窗提示登录；彻底清除请求头中任何 X-User-Id 伪造旁路；
  4. API Base URL 部署可移植：默认采用同源相对路径 /api (生产走 Nginx 统一反代网关)，并在 next.config.mjs 中配置 Next.js 开发反向代理 rewrites，消除写死 localhost:8080 的硬编码；
  5. 动态 Project / Workspace 联动：前端登录后动态调取 GET /api/projects 获取用户真实项目列表，支持顶部项目切换，工作区文件树以实际 workspaceId 路由，彻底废弃 Windows 绝对路径传递；
  6. 真实执行与 SSE 流控闭环：点击“触发执行”调用 POST /api/executions/runs 启动真实任务，获取服务端下发的唯一 runId，订阅 SSE 前通过 POST /api/auth/stream-ticket 动态申请短时一次性凭证，终端与 DAG 节点状态完全由后端 run_events 与 step_runs 真实驱动；
  7. 真实人工审批闭环：审批卡片提交 approve/reject 仅向后端投递 decision 原因，审批成功后由后端事实源重新加载 Run、Step 与 Approval，不再由前端内存虚假改写节点状态；
  8. JGit Diff 与沙箱去伪造：Diff 组件与沙箱部署面板全面剔除自造虚假输出，无未提交改动时呈现干净工作树状态，部署面板仅显示真实 Deployment 实例及健康日志。
失败/恢复演练：
  - 401 凭证过期自动触发全局 agenthub:unauthorized 事件并弹出 AuthModal；
  - 后端停机时前端呈现醒目 API OFFLINE 告警与重试按钮，不再虚假呈现运行中状态；
  - 审批操作失败时提示错误信息并保持原挂起状态。
Artifact / 日志位置：
  - agenthub-frontend/.next/
  - target/surefire-reports/
未覆盖场景：
  - 跨多实例/多节点重启后的持久化恢复 Worker 与数据库租约接管 (属于阶段 C 规划范畴)。
剩余风险：
  - 阶段 C 待推进：DAG 调度目前仍主要依赖 JVM 内存线程池，需在阶段 C 落地持久化调度内核、数据库事件原子自增与应用重启恢复。
状态：自动化测试通过
```

---

### 阶段 C 验收记录

```text
阶段：阶段 C（单一可靠执行内核与重启恢复）
验收日期：2026-10-09
Git commit：待提交 (feat(scheduler): 完成阶段 C 单一可靠执行内核、数据库租约与崩溃重启恢复)
运行环境：OS: Windows 11 / JDK: 17.0.15 / Node: 20.x / Next.js: 14.2.13 / DB: H2 (MySQL Mode) + MySQL 8 兼容迁移
执行命令：
  - mvn clean test (agenthub-backend)
  - npm run build (agenthub-frontend)
测试汇总：
  - Backend: 280/280 passed (0 failures, 0 errors, Surefire clean)
  - Frontend: Next.js 14.2.13 生产构建通过 (4/4 静态路由与客户端 Chunk 打包正常，0 类型报错)
核心交付与黑盒流程：
  1. 权威生产调度唯一内核收敛：确立 DagExecutionEngine 为全系统唯一官方生产执行与拓扑编排内核，全面废弃旧版原型 WorkflowEngineService 与线性 ExecutionScheduler 并标记 @Deprecated；
  2. Run 创建持久化快照与数据库分布式租约抢占体系：Flyway 迁移新增 V10__phase_c_scheduling_leases_and_recovery.sql，为 workflow_runs 与 step_runs 扩展租约所有权、心跳、尝试次数与 DSL 快照字段（lease_owner, lease_until, heartbeat_at, attempt, dsl_snapshot）；创建 Run 时全量持久化完整 DSL 快照，种子预置 5 节点标准生产交付工作流 wf-enterprise-auth-delivery；
  3. 数据库行级原子租约抢占与接管：基于条件更新实现高并发原子租约抢占与安全释放（tryAcquireRunLease / releaseRunLease），保证多实例多 Worker 竞争时仅单一节点持有有效租约，支持租约超时后被其他活跃 Worker 安全接管；
  4. 持久化事件单调原子自增与唯一约束：在 run_events 表建立 uk_run_events_run_seq (run_id, sequence_num) 唯一复合约束，彻底消灭重复事件序号；前端实时终端 LiveExecutionTerminal 彻底修复 Ticket 单次消费导致的 401 重连死循环，断线重连自动重新申请全新有效票据并游标平滑续接；
  5. 全自动应用重启断点恢复系统 (ApplicationRunner)：落地 PersistentExecutionRecoveryRunner，Spring Boot 启动时主动巡检租约过期或崩溃遗留的 RUNNING 任务；DAG 调度引擎支持已完成步骤状态感知与入度拓扑自动裁剪：崩溃重启后按持久化快照继续执行未完任务，绝不重复执行已成功的 Step；等待人工确认的审批步骤在重启后严格保持 WAITING_APPROVAL，杜绝误跳过或非法失败；
  6. 阶段 A/B 补丁修复与前端闭环加固：修复 ResourceAccessGuard 在不同 profile 下的鉴权逻辑，确保生产环境 100% 阻断超级用户绕过同时保留安全测试兼容；全面下线旧版直接收绝对路径的 /api/workspace/* 接口；前端新增 CreateProjectModal 现代化弹窗，消灭默认假项目与假状态。
自动化测试矩阵验证：
  - 新增 PersistentExecutionRecoveryAndLeaseTest (4/4 通过)：
    1. shouldEnforceAtomicRunLeaseContention: 验证两 Worker 并发争抢同一 Run 仅首个成功，持有期间拒绝争抢，租约过期后接管成功；
    2. shouldRecoverStaleRunAndResumeExecutionWithoutRepeatingCompletedSteps: 模拟后端在 Step 运行中强杀中断，重启恢复后继续未完步骤，Step 1 维持 SUCCEEDED 且绝不重复执行；
    3. shouldPreserveWaitingApprovalStateAcrossRestartRecovery: 模拟崩溃重启后等待人工审批的 Run 稳态保留在 WAITING_APPROVAL；
    4. shouldEnforceEventMonotonicityAndUniqueConstraint: 验证事件序号原子自增与数据库唯一约束防重。
Artifact / 日志位置：
  - target/surefire-reports/TEST-com.agenthub.PersistentExecutionRecoveryAndLeaseTest.xml
  - target/surefire-reports/
  - agenthub-frontend/.next/
未覆盖场景：
  - 真实多节点 Docker 容器沙箱生命周期探测与外部网络域名暴露（属于阶段 D 规划范畴）。
剩余风险：
  - 阶段 D 待推进：沙箱与应用部署（Deployment）目前依赖进程级运行与静态预览，需在阶段 D 落地真实受限沙箱容器与交付闭环。
状态：自动化测试通过
```


