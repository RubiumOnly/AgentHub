# AgentHub 技术答辩要点

> 本文服务于面试和架构交流，不是产品能力证明。事实以源码、测试报告、目标环境记录和 [当前能力边界](CURRENT_CAPABILITY_BOUNDARIES.md) 为准；不能由源码、测试或目标环境记录证明的能力，不做超出证据范围的完整度或上线状态承诺。

## 1. 项目定位

AgentHub 是一个面向研发变更交付的模块化 Java 单体。它的核心价值是把多 Agent 调用放进可恢复的 Run/Step 执行模型，让用户在代码变更进入预览前看到计划、工具、Diff、测试、成本和审批证据。

核心链路：

```text
Project -> Workspace -> Workflow Template -> Run/Step -> Diff/Artifact
         -> Quality Gate -> Approval -> Preview Deployment -> Evidence
```

系统明确区分 `DEMO_SIMULATED`、`LIVE_SELF_HOSTED` 和未来的 `LIVE_TEAM`。Mock、Provider fallback 和真实 Provider 不能混写成同一种成功。

## 2. 三个主要技术取舍

### 模块化单体，而不是立即拆微服务

Run、Step、Event、Approval 和 Artifact 频繁协同，当前共享 MySQL 事务能降低本地部署和调试成本。包级边界、Application 接口和 ArchUnit 防止单体退化。只有真实负载或隔离需求触发时，才拆 Worker 或事件平面。

### 数据库租约，而不是先引入 Temporal/Kafka

AI 工作流节点通常持续数秒到数分钟，`workflow_runs` 和 `step_runs` 中的租约、心跳、attempt 和 DSL snapshot 足以支持单节点和有限 Worker 的恢复。调度语义是 at-least-once 加幂等，不宣称 exactly-once；外部副作用必须有 requestId、尝试号和补偿路径。

### 持久化 SSE 和服务端 Workspace

RunEvent 先落库再推送，断线后按游标补回；Workspace 只接收内部 ID 和相对路径，JGit 记录基线、Diff 和回滚。这样页面状态不会成为事实来源，客户端也不能直接指定宿主文件路径。

## 3. 面试中必须主动说明的边界

- 当前实时 SSE、取消和审批唤醒仍有单节点内存部分，多实例广播尚未形成完整产品能力。
- Workspace、JGit 和 Artifact 默认使用本地受控卷，尚未接入对象存储。
- Local Process/Docker sandbox 适合受控团队内部预览，不等于公网任意代码的内核级隔离。
- 当前资源隔离主要是应用层归属校验，细粒度组织 RBAC、物理分库和专用租户隔离属于后续阶段。
- 本地/H2、CI/MySQL、Docker、浏览器和真实 Provider 的验证结论必须分别报告。

## 4. 高频问题的简短回答

**进程被强杀会怎样？** 运行中的租约过期后由恢复 Runner 接管；已成功 Step 从持久化输出恢复，未完成 Step 按 attempt 重试，等待审批的 Run 保持等待。

**SSE 断线会丢消息吗？** 事件先落库并带序号，重连携带 `Last-Event-ID` 回放缺失事件。跨实例实时推送仍受单节点内存连接表限制。

**为什么不允许任意命令？** Deployment 绑定已接受 Artifact 和受控模板，命令白名单、环境清洗、资源配额、超时强杀和 HTTP 健康探针共同限制副作用。

**多 Agent 的差异在哪里？** 关键不在角色数量，而在版本化 AgentDefinition、工具权限、上下文来源、预算、停止条件和可审查 Artifact。IM/Team 是协作手段，不是产品终点。

## 5. 可复现入口

- 架构和数据流：[ARCHITECTURE.md](ARCHITECTURE.md)
- REST/SSE 契约：[API.md](API.md)
- E2E 演练：[E2E_DEMO_GUIDE.md](E2E_DEMO_GUIDE.md)
- 长期路线：[AGENTHUB_PRODUCT_LONG_TERM_PLAN.md](AGENTHUB_PRODUCT_LONG_TERM_PLAN.md)
- 架构决策：[adr/](adr/)
- 关键自动化验证入口：`PersistentExecutionRecoveryAndLeaseTest`、`HighConcurrencyStressIntegrationTest`、`PhaseFProductQualityAndEvidenceBundleIntegrationTest`、`Phase9BaselineAndGovernanceGuardTest`
