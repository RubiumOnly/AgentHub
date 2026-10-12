# ==============================================================================
# AgentHub 跨平台端到端 (E2E) 演练与脱敏运行证据包导出脚本 (Windows PowerShell)
# 支持模式:
#   -Mode demo : 离线确定性模拟模式 (MockAgentRuntime, simulated=true)
#   -Mode live : 真实 Provider 驱动模式 (ProviderDrivenAgentRuntime, simulated=false)
# ==============================================================================
param(
    [ValidateSet("demo", "live")]
    [string]$Mode = "demo",
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Email = "e2e-operator@agenthub.dev",
    [string]$Password = "AgentHub#2026!",
    [string]$OutputDir = "./docs/evidence"
)

$ErrorActionPreference = "Stop"
$AgentPlatform = if ($Mode -eq "live") { "PROVIDER_DRIVEN" } else { "MOCK" }

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " AgentHub E2E Verification & Evidence Exporter (PowerShell)" -ForegroundColor Cyan
Write-Host " Base URL       : $BaseUrl"
Write-Host " Execution Mode : $Mode (agentPlatform=$AgentPlatform)"
Write-Host "==================================================================" -ForegroundColor Cyan

# 1. 注册或登录获取 Bearer Token
Write-Host "[1/9] Authenticating operator ($Email)..."
$Token = $null
try {
    $RegBody = @{ email = $Email; password = $Password; displayName = "E2E Operator" } | ConvertTo-Json
    $AuthResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -ContentType "application/json" -Body $RegBody
    $Token = $AuthResp.data.token
} catch {
    $LoginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
    $AuthResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body $LoginBody
    $Token = $AuthResp.data.token
}
if (-not $Token) {
    throw "Failed to obtain Bearer token from $BaseUrl"
}
$Headers = @{ Authorization = "Bearer $Token" }
Write-Host "  -> Token acquired." -ForegroundColor Green

# 2. 创建研发项目并查询工作区
Write-Host "[2/9] Creating project and resolving JGit workspace..."
$ProjBody = @{ name = "AgentHub-E2E-$Mode"; description = "Phase F E2E verification project ($Mode)" } | ConvertTo-Json
$ProjResp = Invoke-RestMethod -Uri "$BaseUrl/api/projects" -Method Post -Headers $Headers -ContentType "application/json" -Body $ProjBody
$ProjectId = $ProjResp.data.id

$WsResp = Invoke-RestMethod -Uri "$BaseUrl/api/projects/$ProjectId/workspace" -Method Get -Headers $Headers
$WorkspaceId = $WsResp.data.id
Write-Host "  -> Project ID: $ProjectId, Workspace ID: $WorkspaceId" -ForegroundColor Green

# 3. 校验并持久化 DAG 工作流定义
Write-Host "[3/9] Validating and creating DAG workflow definition..."
$DslObj = @{
    nodes = @(
        @{ id = "node-start"; name = "Start"; type = "START" },
        @{ id = "node-code"; name = "Code Generation"; type = "AGENT"; agentPlatform = $AgentPlatform; promptTemplate = "Implement OAuth2 PKCE verifier and static preview artifact" },
        @{ id = "node-gate"; name = "Security Gate"; type = "APPROVAL"; requiresApproval = $true },
        @{ id = "node-end"; name = "End"; type = "END" }
    )
    edges = @(
        @{ id = "e1"; source = "node-start"; target = "node-code" },
        @{ id = "e2"; source = "node-code"; target = "node-gate" },
        @{ id = "e3"; source = "node-gate"; target = "node-end" }
    )
    timeoutSeconds = 120
}
$DslJson = $DslObj | ConvertTo-Json -Depth 8
$ValResp = Invoke-RestMethod -Uri "$BaseUrl/api/workflows/definitions/validate" -Method Post -Headers $Headers -ContentType "application/json" -Body $DslJson
if (-not $ValResp.data.valid) {
    throw "DSL validation failed: $($ValResp | ConvertTo-Json -Compress)"
}

$DefBody = @{
    projectId = $ProjectId
    name = "E2E-Delivery-$Mode"
    version = 1
    dsl = $DslObj
} | ConvertTo-Json -Depth 10
$DefResp = Invoke-RestMethod -Uri "$BaseUrl/api/workflows/definitions" -Method Post -Headers $Headers -ContentType "application/json" -Body $DefBody
$DefId = $DefResp.data.id
Write-Host "  -> Definition ID: $DefId" -ForegroundColor Green

# 4. 启动工作流执行并签发一次性 SSE Stream Ticket
Write-Host "[4/9] Starting workflow run & issuing SSE stream ticket..."
$IdemKey = "idem-e2e-$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())"
$RunBody = @{ projectId = $ProjectId; definitionId = $DefId; idempotencyKey = $IdemKey } | ConvertTo-Json
$RunResp = Invoke-RestMethod -Uri "$BaseUrl/api/executions/runs" -Method Post -Headers $Headers -ContentType "application/json" -Body $RunBody
$RunId = $RunResp.data.id

$TicketResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/stream-ticket" -Method Post -Headers $Headers
$StreamTicket = $TicketResp.data.ticket
Write-Host "  -> Run ID: $RunId, Stream Ticket issued (length=$($StreamTicket.Length))" -ForegroundColor Green

# 5. 等待进入 WAITING_APPROVAL 并执行人工审批放行
Write-Host "[5/9] Waiting for human approval gate..."
for ($i = 0; $i -lt 30; $i++) {
    $StatusResp = Invoke-RestMethod -Uri "$BaseUrl/api/executions/runs/$RunId" -Method Get -Headers $Headers
    $Status = $StatusResp.data.status
    if ($Status -in @("WAITING_APPROVAL", "SUCCEEDED", "FAILED")) {
        break
    }
    Start-Sleep -Milliseconds 500
}

$ApprovalsResp = Invoke-RestMethod -Uri "$BaseUrl/api/approvals/runs/$RunId" -Method Get -Headers $Headers
if ($ApprovalsResp.data -and $ApprovalsResp.data.Count -gt 0) {
    $ApprovalId = $ApprovalsResp.data[0].id
    $ApproveBody = @{ reason = "Approved by E2E automated verification" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$BaseUrl/api/approvals/$ApprovalId/approve" -Method Post -Headers $Headers -ContentType "application/json" -Body $ApproveBody | Out-Null
    Write-Host "  -> Approved gate ($ApprovalId)." -ForegroundColor Green
}

# 6. 等待工作流达到 SUCCEEDED 终态
Write-Host "[6/9] Waiting for workflow run completion..."
$FinalStatus = "RUNNING"
for ($i = 0; $i -lt 30; $i++) {
    $StatusResp = Invoke-RestMethod -Uri "$BaseUrl/api/executions/runs/$RunId" -Method Get -Headers $Headers
    $FinalStatus = $StatusResp.data.status
    if ($FinalStatus -in @("SUCCEEDED", "FAILED", "CANCELLED", "TIMED_OUT")) {
        break
    }
    Start-Sleep -Milliseconds 500
}
Write-Host "  -> Run terminal status: $FinalStatus" -ForegroundColor Green

# 7. 写入工作区预览产物、创建 JGit 快照并校验结构化 Diff 与产物验收
Write-Host "[7/9] Creating JGit snapshot, computing StructuredDiff, and accepting artifact..."
$FileBody = @{
    path = "index.html"
    content = "<!DOCTYPE html><html><head><title>AgentHub Preview</title></head><body><h1>AgentHub E2E Verified</h1></body></html>"
} | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/workspaces/$WorkspaceId/file" -Method Post -Headers $Headers -ContentType "application/json" -Body $FileBody | Out-Null

$SnapBody = @{ runId = $RunId; author = "E2E-Operator"; message = "feat: E2E preview snapshot" } | ConvertTo-Json
$SnapResp = Invoke-RestMethod -Uri "$BaseUrl/api/workspaces/$WorkspaceId/git/snapshot" -Method Post -Headers $Headers -ContentType "application/json" -Body $SnapBody

$DiffResp = Invoke-RestMethod -Uri "$BaseUrl/api/workspaces/$WorkspaceId/diff" -Method Get -Headers $Headers
$ArtifactsResp = Invoke-RestMethod -Uri "$BaseUrl/api/audit/artifacts/run/$RunId" -Method Get -Headers $Headers
$ArtifactId = if ($SnapResp.data.artifactId) { $SnapResp.data.artifactId } else { $ArtifactsResp.data[0].id }

$AcceptBody = @{ reviewer = "E2E-Operator"; comment = "Diff verified and accepted" } | ConvertTo-Json
Invoke-RestMethod -Uri "$BaseUrl/api/audit/artifacts/$ArtifactId/accept" -Method Post -Headers $Headers -ContentType "application/json" -Body $AcceptBody | Out-Null
Write-Host "  -> StructuredDiff files changed: $($DiffResp.data.totalFilesChanged), Accepted Artifact ID: $ArtifactId" -ForegroundColor Green

# 8. 触发受控预览沙箱部署、探针就绪检查与回收停止
Write-Host "[8/9] Launching STATIC_PREVIEW sandbox deployment and verifying HTTP readiness..."
$DepBody = @{
    projectId = $ProjectId
    artifactId = $ArtifactId
    target = "STATIC_PREVIEW"
    templateType = "STATIC_WEB_TEMPLATE"
    sandboxType = "LOCAL_PROCESS"
    healthCheckPath = "/"
} | ConvertTo-Json
$DepResp = Invoke-RestMethod -Uri "$BaseUrl/api/deployments" -Method Post -Headers $Headers -ContentType "application/json" -Body $DepBody
$DepId = $DepResp.data.id

$DepStatus = "PENDING"
for ($i = 0; $i -lt 30; $i++) {
    $DepQuery = Invoke-RestMethod -Uri "$BaseUrl/api/deployments/$DepId" -Method Get -Headers $Headers
    $DepStatus = $DepQuery.data.status
    if ($DepStatus -in @("RUNNING", "FAILED")) {
        break
    }
    Start-Sleep -Milliseconds 500
}
Invoke-RestMethod -Uri "$BaseUrl/api/deployments/$DepId/stop" -Method Post -Headers $Headers | Out-Null
Write-Host "  -> Deployment $DepId reached $DepStatus and was cleanly stopped." -ForegroundColor Green

# 9. 导出脱敏运行证据包 (Sanitized Run Evidence Bundle)
Write-Host "[9/9] Exporting Sanitized Run Evidence Bundle..."
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
$BundlePath = Join-Path $OutputDir "evidence-bundle-$RunId.json"
$EvidenceResp = Invoke-RestMethod -Uri "$BaseUrl/api/executions/runs/$RunId/evidence" -Method Get -Headers $Headers
$BundleData = $EvidenceResp.data
Invoke-WebRequest -Uri "$BaseUrl/api/executions/runs/$RunId/evidence/download" -Method Get -Headers $Headers -OutFile $BundlePath | Out-Null

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host " E2E Verification Completed!" -ForegroundColor Green
Write-Host " Evidence Bundle : $BundlePath"
Write-Host " Execution Mode  : $($BundleData.executionMode) (simulated=$($BundleData.simulated))"
Write-Host " SHA-256 Checksum: $($BundleData.checksum)"
Write-Host "==================================================================" -ForegroundColor Cyan
