# AgentHub 端到端 (E2E) 演练与证据包导出指南

> **文档对齐声明**：本指南中的所有 REST 接口路径、请求体字段、状态枚举与 SSE 鉴权参数均已与后端 `@RestController` 实现及 `PhaseFProductQualityAndEvidenceBundleIntegrationTest` 自动化测试严格对齐。同时提供 **Linux / macOS (Bash)** 与 **Windows (PowerShell)** 双平台可执行命令。

---

## 一、 两种运行模式的显式区分：Demo Mode vs Live Provider Mode

AgentHub 在架构、API 响应与运行证据包（Evidence Bundle）中严格区分两种执行模式，绝不将模拟输出伪装为真实大模型调用：

| 维度 | 离线确定性演示模式 (Demo Mode) | 真实 Provider 驱动模式 (Live Provider Mode) |
| :--- | :--- | :--- |
| **适用场景** | 零外部 API Key 依赖的本地快速体验、CI 确定性回归测试、状态机与审批演练 | 接入真实 DeepSeek / OpenAI / Anthropic / Gemini / Ollama 端点进行真实代码生成与路由演练 |
| **DSL 节点配置** | `"agentPlatform": "MOCK"` | `"agentPlatform": "PROVIDER_DRIVEN"`（或具体平台如 `"DEEPSEEK"`） |
| **执行运行时** | `MockAgentRuntime` (`RuntimeDescriptor.simulated = true`) | `ProviderDrivenAgentRuntime` (`RuntimeDescriptor.simulated = false`) |
| **Token 计费审计** | 不写入真实外部 Provider 账单（仅发送演示级 `USAGE` 事件） | 经 `DynamicProviderRouter` 真实请求外部 LLM，并在 `token_usages` 表持久化真实 Token 消耗、实测延迟与估算成本 |
| **证据包标识** | `"simulated": true`, `"executionMode": "DEMO_SIMULATED"` | `"simulated": false`, `"executionMode": "LIVE_PROVIDER"` |
| **一键演练参数** | `--mode demo` (Bash) / `-Mode demo` (PowerShell) | `--mode live` (Bash) / `-Mode live` (PowerShell) |

---

## 二、 环境准备与跨平台一键启动

### 2.1 前置依赖
* **JDK 17+** & **Maven 3.9+**
* **Node.js 20+** & **npm**
* **Docker & Docker Compose v2**（用于启动 MySQL 8.0 或全栈容器）

### 2.2 方式 A：Docker Compose 生产级编排一键启动

**Linux / macOS (Bash)**:
```bash
cp .env.production.example .env.production
# 编辑 .env.production 设置强密码与可选的 DEEPSEEK_API_KEY / OPENAI_API_KEY
./scripts/deploy-prod.sh --env-file .env.production
```

**Windows (PowerShell)**:
```powershell
Copy-Item .env.production.example .env.production
# 编辑 .env.production 设置强密码与可选的 DEEPSEEK_API_KEY / OPENAI_API_KEY
.\scripts\deploy-prod.ps1 -EnvFile .env.production
```

### 2.3 方式 B：前后端本地开发启动

**Linux / macOS (Bash)**:
```bash
# 1. 启动 MySQL 8.0 容器
docker compose up -d mysql

# 2. 启动 Spring Boot 后端 (默认 8080 端口)
cd agenthub-backend && mvn spring-boot:run

# 3. 启动 Next.js 前端控制台 (默认 3000 端口)
cd agenthub-frontend && npm install && npm run dev
```

**Windows (PowerShell)**:
```powershell
# 1. 启动 MySQL 8.0 容器
docker compose up -d mysql

# 2. 启动 Spring Boot 后端 (默认 8080 端口)
Set-Location agenthub-backend; mvn spring-boot:run

# 3. 启动 Next.js 前端控制台 (默认 3000 端口)
Set-Location agenthub-frontend; npm install; npm run dev
```

---

## 三、 跨平台一键 E2E 自动化演练脚本

仓库内置了跨平台端到端演练脚本，自动串联 **注册登录 -> 创建项目与工作区 -> 校验并保存 DAG 定义 -> 启动工作流 -> 签发 SSE 一次性票据 -> 人工审批放行 -> JGit 快照与结构化 Diff -> 产物验收 -> 启动受控静态预览沙箱并探针就绪 -> 停止部署 -> 导出带 SHA-256 校验和的脱敏运行证据包** 全流程：

**Linux / macOS (Bash)**:
```bash
# 运行离线确定性演示模式 (simulated=true)
bash ./scripts/run-e2e-demo.sh --mode demo

# 运行真实 Provider 驱动模式 (需先配置 Provider API Key，simulated=false)
bash ./scripts/run-e2e-demo.sh --mode live
```

**Windows (PowerShell)**:
```powershell
# 运行离线确定性演示模式 (simulated=true)
.\scripts\run-e2e-demo.ps1 -Mode demo

# 运行真实 Provider 驱动模式 (需先配置 Provider API Key，simulated=false)
.\scripts\run-e2e-demo.ps1 -Mode live
```

---

## 四、 分步 API 闭环演练手册（Bash & PowerShell 对照）

以下分步命令均对应真实后端控制器契约，可逐步复制执行：

### 步骤 1：用户注册与登录鉴权 (`AuthController` - `/api/auth`)

> **注意**：系统已移除硬编码伪造账户与 `user-1` 免密后门，首次演练请先调用 `/api/auth/register` 注册真实账号并获取 PBKDF2 + HMAC-SHA256 签名的 Bearer Token。

**Bash**:
```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "e2e-operator@agenthub.dev",
    "password": "AgentHub#2026!",
    "displayName": "E2E Operator"
  }' | jq -r '.data.token')

# 若账号已存在，调用登录接口：
# TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
#   -H "Content-Type: application/json" \
#   -d '{"email":"e2e-operator@agenthub.dev","password":"AgentHub#2026!"}' | jq -r '.data.token')
echo "Token acquired: ${TOKEN:0:24}..."
```

**PowerShell**:
```powershell
$AuthResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" -Method Post -ContentType "application/json" -Body (@{
    email = "e2e-operator@agenthub.dev"
    password = "AgentHub#2026!"
    displayName = "E2E Operator"
} | ConvertTo-Json)
$Token = $AuthResp.data.token
$Headers = @{ Authorization = "Bearer $Token" }
```

### 步骤 2：创建研发项目与受控工作区 (`ProjectController` - `/api/projects`)

**Bash**:
```bash
PROJECT_ID=$(curl -s -X POST http://localhost:8080/api/projects \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Payment-Gateway-Refactor",
    "description": "E2E Verified Multi-Agent Delivery Project"
  }' | jq -r '.data.id')

WORKSPACE_ID=$(curl -s http://localhost:8080/api/projects/$PROJECT_ID/workspace \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data.id')

echo "PROJECT_ID=$PROJECT_ID, WORKSPACE_ID=$WORKSPACE_ID"
```

**PowerShell**:
```powershell
$ProjResp = Invoke-RestMethod -Uri "http://localhost:8080/api/projects" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    name = "Payment-Gateway-Refactor"
    description = "E2E Verified Multi-Agent Delivery Project"
} | ConvertTo-Json)
$ProjectId = $ProjResp.data.id

$WsResp = Invoke-RestMethod -Uri "http://localhost:8080/api/projects/$ProjectId/workspace" -Method Get -Headers $Headers
$WorkspaceId = $WsResp.data.id
```

### 步骤 3：（可选，仅 Live Provider Mode）注册真实大模型 Provider (`ProviderController` - `/api/providers`)

若演练 **Live Provider Mode (`simulated=false`)**，可通过环境变量引用 `env:DEEPSEEK_API_KEY` 注册真实 Provider 并验证连通性：

**Bash**:
```bash
PROVIDER_ID=$(curl -s -X POST http://localhost:8080/api/providers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "providerType": "DEEPSEEK",
    "baseUrl": "https://api.deepseek.com",
    "secretRef": "env:DEEPSEEK_API_KEY",
    "model": "deepseek-chat",
    "priority": 1,
    "weight": 100,
    "capabilities": "code,reasoning,general",
    "costPerMillionInput": 0.14,
    "costPerMillionOutput": 0.28
  }' | jq -r '.data.id')
```

### 步骤 4：校验并创建 DAG 工作流定义 (`WorkflowDefinitionController` - `/api/workflows/definitions`)

> **字段说明**：节点平台字段为 `agentPlatform`（支持 `"MOCK"` 或 `"PROVIDER_DRIVEN"`），提示词字段为 `promptTemplate`，有向边字段为 `source` 与 `target`。

**Bash**:
```bash
DEF_ID=$(curl -s -X POST http://localhost:8080/api/workflows/definitions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"name\": \"Payment-Service-DAG\",
    \"version\": 1,
    \"dsl\": {
      \"nodes\": [
        {\"id\": \"node-start\", \"name\": \"Start\", \"type\": \"START\"},
        {\"id\": \"node-code\", \"name\": \"Code Gen\", \"type\": \"AGENT\", \"agentPlatform\": \"MOCK\", \"promptTemplate\": \"Generate payment verification module\"},
        {\"id\": \"node-gate\", \"name\": \"Security Approval\", \"type\": \"APPROVAL\", \"requiresApproval\": true},
        {\"id\": \"node-end\", \"name\": \"End\", \"type\": \"END\"}
      ],
      \"edges\": [
        {\"id\": \"e1\", \"source\": \"node-start\", \"target\": \"node-code\"},
        {\"id\": \"e2\", \"source\": \"node-code\", \"target\": \"node-gate\"},
        {\"id\": \"e3\", \"source\": \"node-gate\", \"target\": \"node-end\"}
      ],
      \"timeoutSeconds\": 120
    }
  }" | jq -r '.data.id')
echo "DEF_ID=$DEF_ID"
```

**PowerShell**:
```powershell
$DefBody = @{
    projectId = $ProjectId
    name = "Payment-Service-DAG"
    version = 1
    dsl = @{
        nodes = @(
            @{ id = "node-start"; name = "Start"; type = "START" },
            @{ id = "node-code"; name = "Code Gen"; type = "AGENT"; agentPlatform = "MOCK"; promptTemplate = "Generate payment verification module" },
            @{ id = "node-gate"; name = "Security Approval"; type = "APPROVAL"; requiresApproval = $true },
            @{ id = "node-end"; name = "End"; type = "END" }
        )
        edges = @(
            @{ id = "e1"; source = "node-start"; target = "node-code" },
            @{ id = "e2"; source = "node-code"; target = "node-gate" },
            @{ id = "e3"; source = "node-gate"; target = "node-end" }
        )
        timeoutSeconds = 120
    }
} | ConvertTo-Json -Depth 10
$DefResp = Invoke-RestMethod -Uri "http://localhost:8080/api/workflows/definitions" -Method Post -Headers $Headers -ContentType "application/json" -Body $DefBody
$DefId = $DefResp.data.id
```

### 步骤 5：触发工作流运行并订阅持久化 SSE 事件流 (`ExecutionController` - `/api/executions`)

**Bash**:
```bash
RUN_ID=$(curl -s -X POST http://localhost:8080/api/executions/runs \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"definitionId\": \"$DEF_ID\",
    \"idempotencyKey\": \"idem-run-001\"
  }" | jq -r '.data.id')

# 签发单次短效 SSE Stream Ticket（避免在 URL 中暴露长期 Bearer Token）
TICKET=$(curl -s -X POST http://localhost:8080/api/auth/stream-ticket \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data.ticket')

# 支持携带 Last-Event-ID 断点续传订阅 SSE 事件流
curl -N -H "Last-Event-ID: 0" "http://localhost:8080/api/executions/runs/$RUN_ID/stream?ticket=$TICKET"
```

**PowerShell**:
```powershell
$RunResp = Invoke-RestMethod -Uri "http://localhost:8080/api/executions/runs" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    projectId = $ProjectId
    definitionId = $DefId
    idempotencyKey = "idem-run-001"
} | ConvertTo-Json)
$RunId = $RunResp.data.id

$TicketResp = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/stream-ticket" -Method Post -Headers $Headers
$StreamTicket = $TicketResp.data.ticket
```

### 步骤 6：处理人工审批阻塞门禁 (`ApprovalController` - `/api/approvals`)

当 DAG 推进至 `node-gate` 时，工作流状态转为 `WAITING_APPROVAL`：

**Bash**:
```bash
APPROVAL_ID=$(curl -s http://localhost:8080/api/approvals/runs/$RUN_ID \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

curl -s -X POST http://localhost:8080/api/approvals/$APPROVAL_ID/approve \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reason": "Security audit passed, approved for release"}' | jq .
```

**PowerShell**:
```powershell
$Approvals = Invoke-RestMethod -Uri "http://localhost:8080/api/approvals/runs/$RunId" -Method Get -Headers $Headers
$ApprovalId = $Approvals.data[0].id
Invoke-RestMethod -Uri "http://localhost:8080/api/approvals/$ApprovalId/approve" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    reason = "Security audit passed, approved for release"
} | ConvertTo-Json)
```

### 步骤 7：JGit 快照提交、结构化 Diff 审查与产物验收 (`WorkspaceController` & `ArtifactController`)

**Bash**:
```bash
# 1. 写入静态预览文件并创建 JGit 快照提交
curl -s -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/file \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"path":"index.html","content":"<!DOCTYPE html><html><body><h1>AgentHub Preview</h1></body></html>"}'

curl -s -X POST http://localhost:8080/api/workspaces/$WORKSPACE_ID/git/snapshot \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"runId\":\"$RUN_ID\",\"author\":\"E2E Operator\",\"message\":\"feat: add preview page\"}"

# 2. 获取基于 JGit 树对比的结构化 Diff
curl -s http://localhost:8080/api/workspaces/$WORKSPACE_ID/diff \
  -H "Authorization: Bearer $TOKEN" | jq .

# 3. 查询并验收最新快照产物 (按 createdAt DESC 排序，data[0] 为最新 SNAPSHOT 产物；状态流转至 ACCEPTED 后方可部署)
ARTIFACT_ID=$(curl -s http://localhost:8080/api/audit/artifacts/run/$RUN_ID \
  -H "Authorization: Bearer $TOKEN" | jq -r '.data[0].id')

curl -s -X POST http://localhost:8080/api/audit/artifacts/$ARTIFACT_ID/accept \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"reviewer":"E2E Operator","comment":"Verified JGit diff and accepted artifact"}' | jq .
```

**PowerShell**:
```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/workspaces/$WorkspaceId/file" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    path = "index.html"
    content = "<!DOCTYPE html><html><body><h1>AgentHub Preview</h1></body></html>"
} | ConvertTo-Json)

$SnapResp = Invoke-RestMethod -Uri "http://localhost:8080/api/workspaces/$WorkspaceId/git/snapshot" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    runId = $RunId
    author = "E2E Operator"
    message = "feat: add preview page"
} | ConvertTo-Json)

$Diff = Invoke-RestMethod -Uri "http://localhost:8080/api/workspaces/$WorkspaceId/diff" -Method Get -Headers $Headers
$Artifacts = Invoke-RestMethod -Uri "http://localhost:8080/api/audit/artifacts/run/$RunId" -Method Get -Headers $Headers
$ArtifactId = if ($SnapResp.data.artifactId) { $SnapResp.data.artifactId } else { $Artifacts.data[0].id }

Invoke-RestMethod -Uri "http://localhost:8080/api/audit/artifacts/$ArtifactId/accept" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    reviewer = "E2E Operator"
    comment = "Verified JGit diff and accepted artifact"
} | ConvertTo-Json)
```

### 步骤 8：受控沙箱预览部署、HTTP 探针就绪与停止回收 (`DeploymentController` - `/api/deployments`)

> **受控模板契约**：部署接口要求传入已验收的 `artifactId`、受控目标 `target`（`STATIC_PREVIEW` / `JAVA_SPRING_BOOT` / `NODE_NEXTJS` / `DOCKER_NGINX`）以及模板类型 `templateType`（`STATIC_WEB_TEMPLATE` / `NODE_SERVICE_TEMPLATE` / `JAVA_SPRING_TEMPLATE` / `CUSTOM_SANDBOX_COMMAND`）。状态机严格经过 `CREATED -> BUILD -> START -> HEALTH_CHECK -> RUNNING -> STOP -> CLEANUP -> STOPPED / FAILED`，仅在 `HealthCheckProbeService` 收到真实 HTTP 2xx/3xx 响应后才进入 `RUNNING`。

**Bash**:
```bash
DEP_ID=$(curl -s -X POST http://localhost:8080/api/deployments \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"projectId\": \"$PROJECT_ID\",
    \"artifactId\": \"$ARTIFACT_ID\",
    \"target\": \"STATIC_PREVIEW\",
    \"templateType\": \"STATIC_WEB_TEMPLATE\",
    \"sandboxType\": \"LOCAL_PROCESS\",
    \"healthCheckPath\": \"/\"
  }" | jq -r '.data.id')

# 查询部署状态与 lifecycleTrace (CREATED->BUILD->START->HEALTH_CHECK->RUNNING)
curl -s http://localhost:8080/api/deployments/$DEP_ID \
  -H "Authorization: Bearer $TOKEN" | jq .

# 停止预览部署并释放端口 (STOP->CLEANUP->STOPPED)
curl -s -X POST http://localhost:8080/api/deployments/$DEP_ID/stop \
  -H "Authorization: Bearer $TOKEN" | jq .
```

**PowerShell**:
```powershell
$DepResp = Invoke-RestMethod -Uri "http://localhost:8080/api/deployments" -Method Post -Headers $Headers -ContentType "application/json" -Body (@{
    projectId = $ProjectId
    artifactId = $ArtifactId
    target = "STATIC_PREVIEW"
    templateType = "STATIC_WEB_TEMPLATE"
    sandboxType = "LOCAL_PROCESS"
    healthCheckPath = "/"
} | ConvertTo-Json)
$DepId = $DepResp.data.id

$DepDetail = Invoke-RestMethod -Uri "http://localhost:8080/api/deployments/$DepId" -Method Get -Headers $Headers
Invoke-RestMethod -Uri "http://localhost:8080/api/deployments/$DepId/stop" -Method Post -Headers $Headers
```

### 步骤 9：导出脱敏运行证据包 (`GET /api/executions/runs/{runId}/evidence` & `/evidence/download`)

**Bash**:
```bash
# 直接下载服务端签名的脱敏证据包 JSON 文件（保留原始字节流以确保 SHA-256 校验和 100% 匹配）
curl -s http://localhost:8080/api/executions/runs/$RUN_ID/evidence/download \
  -H "Authorization: Bearer $TOKEN" -o evidence-bundle-$RUN_ID.json

# 验证脱敏状态、运行模式与 SHA-256 校验和
jq '{bundleId, simulated, executionMode, checksum, runStatus: .run.status, eventCount: (.events | length)}' evidence-bundle-$RUN_ID.json
```

**PowerShell**:
```powershell
$Evidence = Invoke-RestMethod -Uri "http://localhost:8080/api/executions/runs/$RunId/evidence" -Method Get -Headers $Headers
Invoke-WebRequest -Uri "http://localhost:8080/api/executions/runs/$RunId/evidence/download" -Method Get -Headers $Headers -OutFile "evidence-bundle-$RunId.json" | Out-Null
$Evidence.data | Select-Object bundleId, simulated, executionMode, checksum
```

* **样本文件参考**：仓库已附带由真实集成测试生成的标准脱敏运行证据包样本 [`docs/evidence/sample-run-evidence-bundle.json`](evidence/sample-run-evidence-bundle.json)，供面试官与代码审计者直接核验数据结构与 SHA-256 防篡改签名。
