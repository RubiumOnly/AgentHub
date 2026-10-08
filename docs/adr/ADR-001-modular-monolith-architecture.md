# ADR-001: 采用模块化单体架构 (Modular Monolith) 替代过早微服务化

* **状态 (Status)**: 已采纳 (Accepted)
* **制定者 (Deciders)**: AgentHub 核心架构组
* **日期 (Date)**: 2026-10-07
* **技术背景 (Technical Story)**: `docs/AGENTHUB_LONG_TERM_PLAN.md` 阶段 0 基线治理与架构选型

---

## 一、 背景与问题陈述 (Context and Problem Statement)

AgentHub 是一个面向复杂研发场景的 Java 多 Agent 协同研发平台。系统业务领域涵盖：
1. **用户与组织权限 (Identity & Access)**：用户身份、权限策略、租户配额；
2. **受控工作区与项目 (Project & Workspace)**：物理工作区路径生命周期、多项目隔离、环境配置；
3. **即时通讯与流式会话 (Conversation & IM)**：多会话管理、@Mention 智能指令路由、交互式协同卡片；
4. **长程任务拓扑与编排 (Orchestration & DAG)**：多阶段有向无环图拆解、审批流、节点状态机；
5. **统一智能体运行时 (Agent Runtime SPI)**：CLI 进程管道、公网 LLM API、本地私有化模型适配器、离线降级模拟器；
6. **代码版本控制与审计 (Audit & JGit)**：基于 Eclipse JGit 的行级差异比对、Unified Diff 补丁生成与版本回退；
7. **本地预览与清单生成 (Sandbox & Manifest)**：Web 页面静态模版预览、Dockerfile 清单导出。

在项目初期（阶段 0~2），系统面临核心架构形态的选择：是直接拆分为分布式微服务集群（Microservices），还是放任传统的无边界技术分层大单体（Layered Monolith），还是采用领域驱动的**模块化单体架构 (Modular Monolith)**？

---

## 二、 决策动因 (Decision Drivers)

1. **研发与调试效率**：当前团队需要极快地进行本地开发、集成测试与端到端联调；单进程内调试断点远快于分布式多服务追踪。
2. **数据与事务一致性**：多 Agent 协同涉及“任务状态变更 -> 消息推送 -> 物理工作区锁占用 -> Git Commit”的联动，进程内事务与 Spring 领域事件能够提供强一致性保障，避免过早引入昂贵的分布式事务与分布式两阶段提交。
3. **演进弹性与防腐蚀**：系统未来可能需要将重算力的 Agent 执行器或沙箱容器抽离为独立 Worker，因此各领域必须具备清晰的高内聚、低耦合边界，严禁模块间网状死锁与循环依赖。
4. **基础设施成本**：避免在业务初期引入服务注册发现 (Nacos/Eureka)、API 网关 (Spring Cloud Gateway)、分布式链路追踪 (SkyWalking) 及 Kubernetes 容器编排运维的巨大开销。

---

## 三、 备选方案 (Considered Options)

1. **方案 A：分布式微服务架构 (Microservices Architecture)**
   - 按业务划分为 `agenthub-gateway`, `agenthub-auth`, `agenthub-im`, `agenthub-orchestrator`, `agenthub-runtime`, `agenthub-workspace` 等 6+ 个独立微服务，通过 OpenFeign / gRPC 远程调用。
2. **方案 B：传统无边界技术分层单体 (Layered Monolith / Big Ball of Mud)**
   - 全局扁平的 `controller`, `service`, `dao`, `entity` 包结构，所有业务逻辑混杂在一个巨型单体中，各个 Service 互相循环注入。
3. **方案 C：领域驱动的模块化单体架构 (Modular Monolith with DDD Boundaries)**
   - 在单个 Spring Boot 3 应用进程中运行，但代码结构严格按照业务领域划分为自治模块包；模块对外暴露精简 Application 接口（Port）或通过 Spring 领域事件通信，严禁跨模块强依赖私有 Entity 与 Repository。

---

## 四、 决策结论 (Decision Outcome)

**采纳方案 C：领域驱动的模块化单体架构 (Modular Monolith)**。

### 核心约束与实施规范

1. **包结构领域自治**：
   - 后端划分为自治领域模块：`identity`, `project`, `workspace`, `conversation`, `orchestration`, `execution`, `runtime`, `audit`, `sandbox`；
   - 每个模块内部遵循 DDD 4 层分层（`adapter`, `application`, `domain`, `infrastructure`）。
2. **模块间通信契约 (Ports & Adapters)**：
   - 跨模块只允许依赖目标模块在 `application` 层公开的精简接口或 DTO；
   - 跨模块业务解耦优先通过 Spring 内置的事件驱动机制 (`ApplicationEventPublisher` / `@EventListener`)；
   - **绝对禁止**跨模块直接 `@Autowired` 注入其他模块的 JPA Repository，禁止直接读写其他模块的内部持久化 Entity。
3. **数据存储策略**：
   - 阶段 0~1 使用共享数据库（单库分表前缀隔离），各模块独占自身的数据表所有权；
   - 严禁跨模块进行复杂的跨领域多表物理 SQL JOIN，聚合数据通过 Application Service 组装。
4. **架构守卫验证**：
   - 在阶段 1 引入 `ArchUnit` 单元测试守卫，自动化校验包依赖规则，将违反模块单向依赖的代码在 CI 阶段直接拦截。

### 正面收益 (Positive Consequences)

* **极速构建与启动**：单个 Maven 模块，本地几秒钟完成编译启动，无需启动中间件集群即可完成全链路调试。
* **低网络开销**：模块间调用为 JVM 内存级方法调用，延迟纳秒级，无序列化/反序列化损耗与网络分区风险。
* **清晰平滑的解耦路径**：每个模块保持天然的输入输出契约与数据边界，未来某模块（如高算力 Agent 运行时）出现性能瓶颈时，可无缝剥离为独立微服务或 Serverless 函数。

### 负面代价与应对 (Negative Consequences & Mitigation)

* **依赖纪律挑战**：在同一个代码库内，开发者容易顺手引入违规包引用。
  * *应对策略*：通过 `ArchUnit` 静态规则强制阻断违规依赖，并在 Code Review 流程中坚决执行包边界审查。
* **部署耦合**：任意单一模块的代码变更都需要重新部署整个后端应用。
  * *应对策略*：在当前项目阶段，功能迭代聚焦于协同链路闭环，单体热部署与自动化测试绿灯足以支撑当前交付节奏。

---

## 五、 各备选方案优缺点对比 (Pros and Cons of the Options)

### 方案 A：分布式微服务架构
* 优势：服务独立部署扩缩容，技术栈可异构。
* 劣势：
  * 过早优化，网络延迟高，调试困难；
  * 分布式事务复杂度极高（Saga/Seata 开销大）；
  * 本地运行需要消耗海量内存与容器资源，极大地阻碍开发者日常迭代。

### 方案 B：传统无边界技术分层单体
* 优势：上手简单，无需考虑领域边界与架构规范。
* 劣势：
  * 极易产生“大泥球”（Big Ball of Mud），依赖关系网状蔓延；
  * 代码腐化速度极快，后期难以测试与维护；
  * 毫无拆分可能，无法向云原生企业级形态演进。

### 方案 C：模块化单体架构 (选定)
* 优势：兼备单体的高开发效率与微服务的清晰模块边界；架构复杂度最低，未来可演进性最高。
* 劣势：需严格依靠工程规范与静态检查工具守护模块边界。

---

## 六、 参考链接 (Links & References)

* `docs/AGENTHUB_LONG_TERM_PLAN.md` - AgentHub 长期产品化演进路线（阶段 0 止血与基线）
* `docs/ARCHITECTURE.md` - 系统整体 DDD 四层分层与架构设计
* Martin Fowler: *MonolithFirst* (https://martinfowler.com/bliki/MonolithFirst.html)
* Architecture Weekly: *Modular Monolith: A Primer*
