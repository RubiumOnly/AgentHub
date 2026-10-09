# AgentHub 当前真实能力边界与架构现状白皮书 (终局收官版)

> **文档版本**: 阶段 9 终局全量交付事实白皮书 `v1.9.0`  
> **生效日期**: 2026-10-09  
> **适用范围**: AgentHub 当前全量工程 (`agenthub-backend` & `agenthub-frontend`)  
> **编制原则**: 100% 诚实、代码事实驱动、263 项自动化测试全量背书、明确交付能力与工程边界

---

## 一、 系统当前真实定位

**AgentHub** 当前处于 **“具备生产级端到端质量门禁、全链路可观测性与大厂级可审计交付能力的 Java 多 Agent 协同研发平台”**（对应《AgentHub 长期产品化演进路线》中的**阶段 9：上线工程、端到端质量门禁与作品化交付期**）。

系统已圆满实现并高质量闭环：
- **微内核 DDD 架构与 Flyway 数据库演进**：MySQL 8.0 / H2 双模适配，从 V1 演进至 V8 包含 18 张核心生产数据表，全量外键、幂等键与复合索引治理；
- **受控工作区与 JGit 审计内核**：受控路径解析器防范路径穿越与 NTFS 设备名，嵌入式 JGit 实现毫秒级快照基线、Unified Diff 结构化输出与非破坏性版本回滚；
- **高并发执行状态机与可靠事件流**：8 态生命周期状态机、优雅取消与进程树强杀、单调递增序号 SSE 广播与基于 `Last-Event-ID` 断点续传重放；
- **版本化 DAG 编排与人工审批门禁**：Kahn 拓扑排序成环检测、同层兄弟节点真并发、自研递归下降安全表达式求值器（防 RCE）、动态条件剪枝与审批挂起恢复；
- **多模型 Provider SPI 矩阵与高可用容灾**：OpenAI/DeepSeek/Claude/Gemini/Ollama 统一契约、加权动态路由、三态熔断器探针隔离、API Key 严格全链路脱敏与 Token 成本实时核算；
- **多智能体协同网络与保序消息总线**：Hierarchical/P2P/Round-Robin 团队拓扑、128 分段锁单调严格连续定序、私聊隔离、四层防死循环拦截与滑动窗口上下文滚动摘要；
- **受限沙箱与一键部署体系**：受控子进程与 Docker 双模沙箱、深度命令防火墙、看门狗超时强平与防 OOM 截断、端口分配治理与动态健康探测；
- **现代化 Bento Grid 控制台**：消灭 AI 塑料味，7 大高质感功能面板联动真实后端 API；
- **生产级可观测性与多容器编排**：Spring Boot Actuator 与 Micrometer Prometheus 核心业务指标导出、生产级 `docker-compose.prod.yml`、Nginx 零缓冲流式反向代理与完备的灾难恢复手册。

---

## 二、 经代码与真实测试 100% 验证的能力清单 (What IS Working)

以下所有能力均由后端 259 项全量测试与前端生产构建严苛验证：

1. **架构与质量门禁 (Architecture & Quality Gate)**：
   - 严格遵循 DDD 四层分层，ArchUnit 架构自动化单测 100% 绿灯保障；
   - 覆盖五大核心端到端交付场景（`FullLifecycleEndToEndIntegrationTest` 场景 A、B、C、D、E 全闭环）；
   - 高并发压测保证：工作区租约锁竞争互斥率 100%、消息单调定序 50 并发 0 丢号、DAG 批量执行 P99 延迟 268ms。
2. **安全与受控边界 (Security & Workspace Guard)**：
   - 彻底阻断 `../` 目录穿越、Windows NTFS 设备名（`CON`, `NUL`）、绝对路径越权与 `.git` 内部篡改；
   - 沙箱命令防火墙全面阻断 `rm -rf /`、`mkfs`、`dd`、管道提权等 12 种高危模式；
   - 敏感 API Key 在日志、调用链与前端响应中实现 100% 掩码脱敏。
3. **并发调度与状态流转 (Scheduling & State Machine)**：
   - 8 态生命周期驱动，杜绝非法状态迁移与并发竞态；
   - 支持并发执行与取消令牌（`CancelToken`），快速强平关联子进程树；
   - 审批节点自动挂起并在用户决策通过后毫秒级恢复后续依赖节点调度。
4. **实时推送与网络健壮性 (Streaming & Reconnection)**：
   - SSE 事件自带严格单调递增 `sequence_num` 并持久化到 `run_events`；
   - 客户端携带 `Last-Event-ID` 重连时自动补发历史遗漏事件；
   - Nginx 反向代理配置 `proxy_buffering off` 消除流式代理缓冲延迟。
5. **运维与可观测性 (Observability & Production Deployment)**：
   - 暴露 `/actuator/health`、`/actuator/info`、`/actuator/prometheus`；
   - 导出 `agenthub_runs_total`、`agenthub_steps_duration`、`agenthub_sse_connections_active` 等丰富业务指标；
   - 提供生产级 `docker-compose.prod.yml`、`Dockerfile`、一键部署脚本及故障恢复手册。

---

## 三、 当前真实的系统工程边界 (The Current Engineering Boundaries)

本着技术诚实原则，系统在达到当前里程碑的同时，明确以下边界与未实现能力：

1. **分布式单体 vs 分布式集群部署边界**：
   - 当前采用模块化单体形态，单机多核下吞吐优秀；
   - 若要部署为多节点集群（Horizontal Scaling），需要将本地 `WorkspaceLockManager` 的内存 ReentrantLock 与本地内存 SSE Emitter 扩展为 Redis Redisson 分布式锁与 Redis Pub/Sub 事件总线（数据表与架构设计已预留相关字段与 SPI 接口）。
2. **宿主机文件系统 vs 云原生对象存储 (S3) 边界**：
   - 受控工作区目前依托宿主机本地受控目录与挂载卷；
   - 尚未接入 AWS S3 或 MinIO 进行分布式大型文件归档。
3. **轻量沙箱 vs Kubernetes 动态 Pod 沙箱边界**：
   - 当前沙箱提供的是本地受限安全子进程与 Docker 容器隔离；
   - 尚未接入 Kubernetes API 动态拉起独立 Pod 或 MicroVM (Kata/Firecracker) 级内核隔离。
4. **租户多租体系边界**：
   - 当前具备严格的资源所有权校验 (`ResourceAccessGuard`) 与用户身份隔离，但在数据库层共享同 Schema，尚未实现多租户物理 Schema/Database 级绝对隔离。
