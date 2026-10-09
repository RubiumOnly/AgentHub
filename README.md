# AgentHub

<p align="center"><strong>企业级多智能体协同研发与可审计交付平台 (Multi-Agent Engineering Platform)</strong></p>

<p align="center">
  <a href="https://github.com/RubiumOnly/AgentHub/actions/workflows/ci.yml"><img src="https://github.com/RubiumOnly/AgentHub/actions/workflows/ci.yml/badge.svg?branch=main" alt="CI" /></a>
  <a href="https://www.oracle.com/java/"><img src="https://img.shields.io/badge/Java-17%20LTS-ED8B00?logo=openjdk&logoColor=white" alt="Java 17" /></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3.4" /></a>
  <a href="https://nextjs.org/"><img src="https://img.shields.io/badge/Next.js-14.2-black?logo=next.js&logoColor=white" alt="Next.js 14" /></a>
  <a href="https://www.typescriptlang.org/"><img src="https://img.shields.io/badge/TypeScript-5.5-3178C6?logo=typescript&logoColor=white" alt="TypeScript" /></a>
  <a href="https://tailwindcss.com/"><img src="https://img.shields.io/badge/Tailwind%20CSS-3.4-38B2AC?logo=tailwind-css&logoColor=white" alt="Tailwind CSS" /></a>
  <a href="https://www.eclipse.org/jgit/"><img src="https://img.shields.io/badge/JGit-6.8%2B-F05032?logo=git&logoColor=white" alt="Eclipse JGit" /></a>
  <a href="https://prometheus.io/"><img src="https://img.shields.io/badge/Prometheus-Actuator%20Ready-E6522C?logo=prometheus&logoColor=white" alt="Prometheus" /></a>
  <a href="https://www.docker.com/"><img src="https://img.shields.io/badge/Docker-Production%20Compose-2496ED?logo=docker&logoColor=white" alt="Docker Compose Ready" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-5C8A72" alt="MIT License" /></a>
</p>

[快速开始](#-快速开始) · [架构白皮书与答辩指南](docs/TECH_WHITEPAPER_AND_DEFENSE.md) · [5~8分钟演示指南](docs/E2E_DEMO_GUIDE.md) · [当前真实能力边界](docs/CURRENT_CAPABILITY_BOUNDARIES.md) · [灾备与故障演练手册](docs/ops/DISASTER_RECOVERY_RUNBOOK.md) · [告警规则](docs/ops/ALERTING_RULES.md) · [长期演进计划 (已圆满交付)](docs/AGENTHUB_LONG_TERM_PLAN.md) · [更新记录](CHANGELOG.md)

---

## 🌟 项目定位与核心愿景

**AgentHub** 是面向企业级复杂研发协同场景打造的**多智能体协同研发与可审计交付平台**。系统聚焦在 AI 驱动的全流程开发闭环，融合了 **严谨的 Java 后端高并发架构 (DDD 四层分层 + ArchUnit 架构防腐守护)** 与 **先进的多智能体协作理论**。

针对大模型单一 Agent 上下文易退化、多 Agent 协作缺乏工程闭环与物理竞态覆写的痛点，AgentHub 提供了：
- **类飞书/Slack 自然协同网络**：支持单聊、多会话并发管理、基于 `@Mention` 的角色路由与 128 分段锁单调严格保序消息总线；
- **DAG 任务拓扑与人工门禁引擎**：DSL 版本化描述，Kahn 拓扑排序成环拦截，同层兄弟节点真正并行，递归下降安全求值器（防 RCE），以及人工审批（Human-in-the-Loop）挂起与恢复；
- **受控工作区与 JGit 结构化审计**：嵌入式 JGit 驱动，每次 Run 建立基线 Tag，Step 完成自动记录 Commit Snapshot 与 Unified Diff 结构化补丁，支持非破坏性安全回滚；
- **多模型 Provider SPI 矩阵与高可用容灾**：统一契约接入 OpenAI、DeepSeek、Anthropic Claude、Google Gemini 与本地 Ollama，支持动态加权路由、三态熔断器探针隔离与 Token 成本实时核算；
- **安全沙箱与一键部署体系**：本地受限子进程与 Docker 双模沙箱隔离，深度命令防火墙，看门狗超时强平与防 OOM 截断，端口动态分配与健康探针探测；
- **现代高级感 Bento Grid 交互台**：彻底消灭“AI 塑料味”，采用 Linear/Vercel 级模块化便当盒网格，集成 7 大核心面板与实时 ANSI 彩色流式执行终端；
- **生产级可观测性与运维就绪**：内置 Spring Boot Actuator 与 Prometheus 指标导出，具备完整的灾难恢复手册与生产级 Docker Compose 编排。

---

## ⚡ 真实性能与压测基线 (无虚假水分)

所有指标均由自动化集成测试与基准压测套件真实测量产生：

| 压测指标项 | 观测数值 | 测试用例依据 |
| :--- | :--- | :--- |
| **全量自动化测试覆盖** | **263 项用例 100% 绿灯** (后端 259 项 + 前端 4 项构建) | `FullLifecycleEndToEndIntegrationTest`, `ArchUnitArchitectureTest` 等 |
| **工作区并发锁竞争互斥率** | **100% 互斥 (Max Holders <= 1)**，死锁发生率 0% | `HighConcurrencyStressIntegrationTest` |
| **多 Agent 消息 128 分段锁单调定序** | **50 并发请求 87ms 内完成分配**，0 丢号，0 倒序 | `HighConcurrencyStressIntegrationTest` |
| **批量 DAG 拓扑执行吞吐** | **5 个复杂工作流 541ms 内全量跑通** (P50: 252ms, P99: 268ms) | `HighConcurrencyStressIntegrationTest` |
| **沙箱命令防火墙拦截率** | **100% 阻断** (覆盖 rm -rf, mkfs, dd, 管道提权等 12 种模式) | `SandboxSecurityGuardAndFirewallTest` |
| **受控路径穿越拦截率** | **100% 阻断** (覆盖 ../, NTFS 设备名, 外部绝对路径, .git 篡改) | `WorkspacePathGuardTest` |

---

## 🚀 快速开始

### 方式一：生产级多容器一键拉起 (推荐，开箱即用)

```bash
# 1. 复制生产环境变量模版
cp .env.production.example .env.production

# 2. 一键构建并启动全栈集群 (MySQL 8.0 + Redis 7 + Backend + Frontend + Nginx)
# Linux/macOS:
./scripts/deploy-prod.sh
# Windows PowerShell:
.\scripts\deploy-prod.ps1
```
启动完成后访问：
- **Web 控制台**：`http://localhost/`
- **REST API**：`http://localhost/api`
- **Actuator 健康探针**：`http://localhost/actuator/health`
- **Prometheus 指标**：`http://localhost/actuator/prometheus`

---

### 方式二：本地研发与单机调试

#### 1. 前置依赖
- **JDK 17 LTS**
- **Maven 3.8+**
- **Node.js 18+ & npm**
- **Git**

#### 2. 后端服务启动 (Spring Boot 8080)
```bash
cd agenthub-backend

# 运行全量 259 项单元测试与集成测试（100% 绿灯）
mvn clean test

# 启动后端服务
mvn spring-boot:run
```

#### 3. 前端工作台启动 (Next.js 3000)
```bash
cd agenthub-frontend

# 安装依赖并启动开发服务器
npm install
npm run dev
```
浏览器访问 `http://localhost:3000` 进入 Bento Grid 协作控制台。

---

## 🏛️ 核心架构与核心设计决策

AgentHub 的设计权衡在三份关键架构决策记录 (ADR) 与白皮书中详细剖析：
- [**ADR-001：为什么采用模块化单体架构 (Modular Monolith)**](docs/adr/ADR-001-modular-monolith-architecture.md)
- [**ADR-002：为什么采用 SSE + 关系库持久化事件流而不是裸 WebSocket**](docs/adr/ADR-002-sse-and-persistent-event-stream.md)
- [**ADR-003：为什么受控工作区不信任客户端绝对路径与 JGit 快照机制**](docs/adr/ADR-003-workspace-distrusts-client-paths.md)
- [**大厂级核心技术白皮书与答辩防穿指南**](docs/TECH_WHITEPAPER_AND_DEFENSE.md)
- [**端到端 5~8 分钟可重复演示指南**](docs/E2E_DEMO_GUIDE.md)

---

## 🤝 贡献与许可证

欢迎通过 GitHub Issues 提出改进建议或 Bug 反馈；提交代码前请阅读 [CONTRIBUTING.md](CONTRIBUTING.md)。  
本项目采用 [MIT License](LICENSE) 开源许可证。
