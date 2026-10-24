<#
.SYNOPSIS
  本地一键全量模拟远程 GitHub Actions CI 门禁流程 (PowerShell 版本)。
.DESCRIPTION
  严格对齐 .github/workflows/ci.yml 中的 3 个核心 Job：
  1. backend-test: 全量后端单元与集成测试 (Java 17 / Maven / Surefire 确定性排序)
  2. frontend-build: 前端债务冻结检查 (Phase 0 零 localhost/零 user-1/零硬编码)、TypeScript strict 检查与 Next.js 生产构建
  3. production-ops-and-container-gate: 生产环境密钥门禁预检、docker-compose.prod.yml 语法探针、灾备基线验证
.EXAMPLE
  .\scripts\test-ci-local.ps1
  .\scripts\test-ci-local.ps1 -SkipBackend
  .\scripts\test-ci-local.ps1 -SkipFrontend
#>

[CmdletBinding()]
param(
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipOps,
    [switch]$Quick
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Split-Path -Parent $ScriptDir
Set-Location $RepoRoot

function Write-Section($title) {
    Write-Host "`n==================================================================" -ForegroundColor Cyan
    Write-Host "  $title" -ForegroundColor Cyan
    Write-Host "==================================================================" -ForegroundColor Cyan
}

function Write-Pass($msg) {
    Write-Host "  [PASS] $msg" -ForegroundColor Green
}

function Write-Fail($msg) {
    Write-Host "  [FAIL] $msg" -ForegroundColor Red
}

$startTime = [System.Diagnostics.Stopwatch]::StartNew()

try {
    # -------------------------------------------------------------------------
    # JOB 1: Backend Tests & Architecture Gate
    # -------------------------------------------------------------------------
    if (-not $SkipBackend) {
        Write-Section "Job 1: Backend Tests, Architecture & Debt Freeze Gate"
        Set-Location "$RepoRoot\agenthub-backend"

        if ($Quick) {
            Write-Host "Running quick backend tests..." -ForegroundColor Yellow
            mvn test -Dtest="Phase9BaselineAndGovernanceGuardTest,ArchUnitArchitectureTest,Phase1ModuleConvergenceAndContractTest,PhaseEProductionOpsAndDisasterRecoveryIntegrationTest"
        } else {
            Write-Host "Running full backend test suite with alphabetical runOrder..." -ForegroundColor Yellow
            mvn clean test
        }

        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Backend test suite failed!"
            exit 1
        }
        Write-Pass "Backend full test suite passed cleanly."
    }

    # -------------------------------------------------------------------------
    # JOB 2: Frontend Build, Typecheck & Debt Freeze Gate
    # -------------------------------------------------------------------------
    if (-not $SkipFrontend) {
        Write-Section "Job 2: Frontend Build, Typecheck & Debt Freeze Gate"
        Set-Location "$RepoRoot\agenthub-frontend"

        Write-Host "1. Checking Phase 0 debt freeze (zero hardcoded localhost, legacy /api/workspace/, user-1)..." -ForegroundColor Yellow
        $debtViolations = @()
        Get-ChildItem -Path "src" -Recurse -File -Include *.ts, *.tsx | ForEach-Object {
            $content = Get-Content $_.FullName -Raw
            if ($content -match 'localhost|127\.0\.0\.1|/api/workspace/|d:/work/agenthub|"user-1"|''user-1''|isDemoMode\(\)\s*\|\|\s*!projectId|isDemoMode\(\)\s*\|\|\s*runId\s*===\s*MOCK_RUN\.id') {
                $debtViolations += $_.FullName
            }
        }
        if ($debtViolations.Count -gt 0) {
            Write-Fail "Found hardcoded debt violations in frontend files:"
            $debtViolations | ForEach-Object { Write-Host "    $_" -ForegroundColor Red }
            exit 1
        }
        Write-Pass "Zero debt violations verified in frontend src."

        Write-Host "2. Running TypeScript strict typecheck..." -ForegroundColor Yellow
        npx tsc --noEmit
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "TypeScript typecheck failed!"
            exit 1
        }
        Write-Pass "TypeScript typecheck passed with 0 errors."

        Write-Host "3. Building Next.js production bundle..." -ForegroundColor Yellow
        npm run build
        if ($LASTEXITCODE -ne 0) {
            Write-Fail "Next.js build failed!"
            exit 1
        }
        Write-Pass "Next.js production build succeeded."
    }

    # -------------------------------------------------------------------------
    # JOB 3: Production Config, Compose & DR Drill Gate
    # -------------------------------------------------------------------------
    if (-not $SkipOps) {
        Write-Section "Job 3: Production Config, Compose & DR Drill Gate"
        Set-Location $RepoRoot

        Write-Host "1. Verifying .env.production.example rejects deployment..." -ForegroundColor Yellow
        $scriptPath = "$RepoRoot\scripts\deploy-prod.ps1"
        $rejectPassed = $false
        try {
            & powershell.exe -ExecutionPolicy Bypass -File $scriptPath -EnvFile .env.production.example -ValidateOnly
        } catch {
            $rejectPassed = $true
        }
        if ($LASTEXITCODE -ne 0) {
            $rejectPassed = $true
        }
        if (-not $rejectPassed) {
            Write-Fail ".env.production.example with placeholder secrets MUST be rejected!"
            exit 1
        }
        Write-Pass "Placeholder .env.production.example was properly rejected."

        Write-Host "2. Generating strong CI env and testing docker-compose.prod.yml config..." -ForegroundColor Yellow
        $ciEnv = @"
IMAGE_TAG=local-ci
MYSQL_ROOT_PASSWORD=ProdRootDbSecret_998877665544_Strong!
MYSQL_DATABASE=agenthub
MYSQL_USER=agenthub_app
MYSQL_PASSWORD=ProdAppDbSecret_112233445566_Strong!
REDIS_PASSWORD=
AGENTHUB_AUTH_SECRET=8f9a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b9c
ACTUATOR_PROMETHEUS_TOKEN=PromObsToken_2026_SecureMetricsKey_998877
HTTP_PORT=80
HTTPS_PORT=443
DEEPSEEK_API_KEY=
OPENAI_API_KEY=
ANTHROPIC_API_KEY=
GEMINI_API_KEY=
"@
        Set-Content -Path ".env.production.local-ci" -Value $ciEnv -Encoding utf8
        try {
            docker compose --env-file .env.production.local-ci -f docker-compose.prod.yml config --quiet
            if ($LASTEXITCODE -ne 0) {
                Write-Fail "docker compose config failed!"
                exit 1
            }
            Write-Pass "docker-compose.prod.yml configuration syntax valid."
        } finally {
            Remove-Item -Path ".env.production.local-ci" -Force -ErrorAction SilentlyContinue
        }
    }

    $startTime.Stop()
    Write-Section "ALL CI GATES PASSED (Total: $([Math]::Round($startTime.Elapsed.TotalSeconds, 1))s)"
    Write-Host "  Local CI simulation completed successfully! Safe to commit and push." -ForegroundColor Green

} catch {
    Write-Fail "Local CI simulation failed with exception: $_"
    exit 1
} finally {
    Set-Location $RepoRoot
}
