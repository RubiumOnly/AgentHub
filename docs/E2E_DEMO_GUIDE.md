# AgentHub 5~8 分钟全链路真实演示指南 (E2E Demo Guide)

> **演示目标**：向技术面试官、架构评审委员会或客户现场完整展示 AgentHub 从“需求输入”到“受控审计与一键部署”的全闭环企业级能力。  
> **准备环境**：已启动的本地集群（`docker compose -f docker-compose.prod.yml up -d` 或本地 `mvn spring-boot:run` + `npm run dev`）。

---

## 演示总览全景图 (8 分钟时间轴)

```text
[0:00~1:00] 登录与受控工作区创建 (Workspace Isolation & JGit Baseline)
     │
[1:00~2:30] 组建多 Agent 团队与 DAG 工作流编排 (Team Topology & Kahn DAG)
     │
[2:30~4:00] 实时流式执行与并发观察 (Real-time SSE Stream & State Machine)
     │
[4:00~5:00] 人工审批门禁交互 (Human-in-the-Loop Approval Action Card)
     │
[5:00~6:30] 产物审查与 Unified Diff 对比 (JGit Diff Engine & Revert Preview)
     │
[6:30~7:30] 一键沙箱自动化部署与实时预览 (Dynamic Port & Health Check Probe)
     │
[7:30~8:00] 生产监控指标与审计闭环展示 (Actuator Prometheus & Run Evidence)
```

---

## 详细操作步骤与 CLI/API 指令

### 步骤 1：创建受控项目与工作区 (0:00 - 1:00)

**动作说明**：系统为项目分配受控的隔离目录，严禁客户端传入服务器绝对路径，并初始化 JGit 初始基线 Commit。

```bash
# 1. 登录系统获取凭据
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@agenthub.local","password":"admin"}' | jq -r '.data.token')

# 2. 创建受控项目
PROJECT_RESP=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"OrderService-Refactor","description":"企业级订单系统重构"}')

PROJECT_ID=$(echo $PROJECT_RESP | jq -r '.data.id')
WORKSPACE_ID=$(echo $PROJECT_RESP | jq -r '.data.workspaceId')
echo "Project created: $PROJECT_ID, Workspace: $WORKSPACE_ID"
```

---

### 步骤 2：提交多 Agent 协同 DAG 计划 (1:00 - 2:30)

**动作说明**：定义版本化 DSL，声明 Backend 与 Frontend 并行开发节点、QA 自动化测试节点、上线人工门禁节点及自动交付节点。系统使用 Kahn 拓扑排序校验无环路。

```bash
# 提交 DAG 工作流定义
WF_RESP=$(curl -s -X POST http://localhost:8080/api/workflow-definitions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "key": "wf-order-v1",
    "name": "订单重构端到端交付流水线",
    "dsl": {
      "nodes": [
        {"id": "backend_dev", "name": "后端接口开发", "type": "AGENT", "runtime": "MOCK", "prompt": "实现订单幂等支付接口"},
        {"id": "frontend_dev", "name": "前端收银台开发", "type": "AGENT", "runtime": "MOCK", "prompt": "重构 Bento 收银台页面"},
        {"id": "qa_verify", "name": "自动化测试门禁", "type": "AGENT", "runtime": "MOCK", "prompt": "执行边界防御测试"},
        {"id": "human_gate", "name": "上线人工门禁", "type": "APPROVAL", "requiresApproval": true},
        {"id": "deploy_step", "name": "沙箱自动化交付", "type": "AGENT", "runtime": "MOCK", "prompt": "构建并部署预览容器"}
      ],
      "edges": [
        {"from": "backend_dev", "to": "qa_verify"},
        {"from": "frontend_dev", "to": "qa_verify"},
        {"from": "qa_verify", "to": "human_gate"},
        {"from": "human_gate", "to": "deploy_step"}
      ]
    }
  }')
```

---

### 步骤 3：启动运行与 SSE 实时流式监控 (2:30 - 4:00)

**动作说明**：启动工作流运行，打开 SSE 长连接，观察单调保序的事件实时流下发。

```bash
# 1. 启动任务实例
RUN_RESP=$(curl -s -X POST http://localhost:8080/api/runs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"definitionKey\":\"wf-order-v1\",\"name\":\"第 1 次发布验证\"}")

RUN_ID=$(echo $RUN_RESP | jq -r '.data.id')

# 2. 命令行或前端面板订阅 SSE 实时事件流
curl -N -H "Authorization: Bearer $TOKEN" "http://localhost:8080/api/runs/$RUN_ID/stream"
```

*在 Web 控制台的 Bento Grid 界面中，LiveExecutionTerminal 面板将以 ANSI 颜色高亮实时滚动打印节点执行进度。*

---

### 步骤 4：人工审批决策门禁 (4:00 - 5:00)

**动作说明**：DAG 引擎推进至 `human_gate` 时自动挂起，Step 状态进入 `WAITING_APPROVAL`。前端界面高亮浮现 `ApprovalActionCard`。

```bash
# 1. 查询待审批项
APPROVAL_ID=$(curl -s -X GET "http://localhost:8080/api/approvals?runId=$RUN_ID" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

# 2. 审批人审查测试报告后点击通过
curl -s -X POST "http://localhost:8080/api/approvals/$APPROVAL_ID/decision" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"decision":"APPROVED","comments":"代码审查与自动化单测 100% 绿灯，准予上线"}'
```

*通过后，DAG 调度器接收唤醒信号，自动恢复调度 `deploy_step`。*

---

### 步骤 5：JGit 结构化 Diff 审查与非破坏性回滚 (5:00 - 6:30)

**动作说明**：在 DiffArtifactReviewer 面板中查看工作区文件增删变化（Unified Patch）。如果发现某个文件不符合预期，可一键安全回滚至快照基线。

```bash
# 查询运行产生的文件变更 Diff
curl -s -X GET "http://localhost:8080/api/runs/$RUN_ID/diff" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

---

### 步骤 6：一键沙箱部署与 Web 预览 (6:30 - 7:30)

**动作说明**：调用沙箱部署接口，系统动态分配非冲突端口（如 `:18080`），完成服务构建并执行健康探针探测，前端内嵌实时预览 iframe。

```bash
# 创建并启动本地沙箱部署
DEP_RESP=$(curl -s -X POST http://localhost:8080/api/deployments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"$PROJECT_ID\",\"target\":\"LOCAL_PREVIEW\"}")

DEPLOY_ID=$(echo $DEP_RESP | jq -r '.data.id')
echo "Deployment online at: $(echo $DEP_RESP | jq -r '.data.url')"
```

---

### 步骤 7：监控观测指标与证据闭环 (7:30 - 8:00)

**动作说明**：访问 `/actuator/prometheus`，展示所有被记录的真实执行 Counter、Gauge 和 Timer，证明全链路非模拟伪造。

```bash
# 查看业务监控指标导出
curl -s http://localhost:8080/actuator/prometheus | grep "agenthub_"
```
