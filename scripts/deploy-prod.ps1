# ==============================================================================
# AgentHub Enterprise Production Deployment & Pre-Flight Security Gate (PowerShell)
# Usage:
#   .\scripts\deploy-prod.ps1 [-EnvFile .env.production] [-ValidateOnly]
# ==============================================================================
param(
    [string]$EnvFile = ".env.production",
    [switch]$ValidateOnly = $false
)

$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " AgentHub Production Pre-Flight Security & Deployment Gate" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

if (-not (Test-Path $EnvFile)) {
    Write-Host "[ERROR] Missing $EnvFile!" -ForegroundColor Red
    Write-Host "Please copy .env.production.example to $EnvFile and configure strong production secrets." -ForegroundColor Yellow
    exit 1
}

$EnvMap = @{}
Get-Content $EnvFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
        $idx = $line.IndexOf("=")
        $k = $line.Substring(0, $idx).Trim()
        $v = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
        $EnvMap[$k] = $v
    }
}

function Test-ForbiddenPlaceholder([string]$val) {
    if ([string]::IsNullOrWhiteSpace($val)) { return $false }
    $lower = $val.ToLowerInvariant()
    foreach ($p in @("changeme", "your-", "placeholder", "provide_strong_random", "secret-key-only-for-local", "phase1-2026-superstrong", "example")) {
        if ($lower.Contains($p)) { return $true }
    }
    return $false
}

function Test-WeakPassword([string]$val) {
    if ([string]::IsNullOrWhiteSpace($val)) { return $false }
    $lower = $val.ToLowerInvariant().Trim()
    return @("password", "123456", "12345678", "123456789", "1234567890", "admin", "admin123", "root", "root123", "redis", "mysql", "agenthub", "qwerty") -contains $lower
}

$Violations = @()

$authSecret = $EnvMap["AGENTHUB_AUTH_SECRET"]
if ([string]::IsNullOrWhiteSpace($authSecret)) {
    $Violations += "AGENTHUB_AUTH_SECRET is missing or empty"
} elseif ($authSecret.Length -lt 32) {
    $Violations += "AGENTHUB_AUTH_SECRET must be at least 32 characters (actual: $($authSecret.Length))"
} elseif ((Test-ForbiddenPlaceholder $authSecret) -or (Test-WeakPassword $authSecret)) {
    $Violations += "AGENTHUB_AUTH_SECRET contains forbidden placeholder or weak secret"
}

foreach ($dbKey in @("MYSQL_ROOT_PASSWORD", "MYSQL_PASSWORD")) {
    $dbVal = $EnvMap[$dbKey]
    if ([string]::IsNullOrWhiteSpace($dbVal)) {
        $Violations += "$dbKey is missing or empty"
    } elseif ($dbVal.Length -lt 12) {
        $Violations += "$dbKey must be at least 12 characters (actual: $($dbVal.Length))"
    } elseif ((Test-ForbiddenPlaceholder $dbVal) -or (Test-WeakPassword $dbVal)) {
        $Violations += "$dbKey contains forbidden placeholder or weak password"
    }
}

$redisPass = $EnvMap["REDIS_PASSWORD"]
if (-not [string]::IsNullOrWhiteSpace($redisPass)) {
    if ($redisPass.Length -lt 12 -or (Test-ForbiddenPlaceholder $redisPass) -or (Test-WeakPassword $redisPass)) {
        $Violations += "REDIS_PASSWORD must be >=12 chars and free of placeholders/weak passwords when set"
    }
}

$scrapeToken = $EnvMap["ACTUATOR_PROMETHEUS_TOKEN"]
if (-not [string]::IsNullOrWhiteSpace($scrapeToken)) {
    if ($scrapeToken.Length -lt 16 -or (Test-ForbiddenPlaceholder $scrapeToken) -or (Test-WeakPassword $scrapeToken)) {
        $Violations += "ACTUATOR_PROMETHEUS_TOKEN must be >=16 chars and free of placeholders"
    }
}

foreach ($apiKeyName in @("DEEPSEEK_API_KEY", "ANTHROPIC_API_KEY", "OPENAI_API_KEY", "GEMINI_API_KEY")) {
    $apiKeyVal = $EnvMap[$apiKeyName]
    if (-not [string]::IsNullOrWhiteSpace($apiKeyVal) -and (Test-ForbiddenPlaceholder $apiKeyVal)) {
        $Violations += "$apiKeyName still contains sample placeholder; leave blank if unused or provide real secret"
    }
}

if ($Violations.Count -gt 0) {
    foreach ($v in $Violations) {
        Write-Host "[FATAL] $v" -ForegroundColor Red
    }
    Write-Host "[ABORT] Production pre-flight check failed with $($Violations.Count) violation(s). Refusing to start." -ForegroundColor Red
    exit 1
}

Write-Host "[OK] Production environment file ($EnvFile) passed all security & secret checks." -ForegroundColor Green

if ($ValidateOnly) {
    exit 0
}

Write-Host "[1/4] Checking prerequisites..." -ForegroundColor Green
try {
    docker --version | Out-Null
    docker compose version | Out-Null
} catch {
    Write-Host "[ERROR] Docker or Docker Compose is not installed or not in PATH." -ForegroundColor Red
    exit 1
}

$GitSha = "1.0.0"
try {
    $GitSha = (git rev-parse --short HEAD).Trim()
} catch {}
$env:IMAGE_TAG = $GitSha

Write-Host "[2/4] Building production container images tagged with commit SHA ($GitSha)..." -ForegroundColor Green
docker compose --env-file $EnvFile -f docker-compose.prod.yml build

Write-Host "[3/4] Launching production cluster..." -ForegroundColor Green
docker compose --env-file $EnvFile -f docker-compose.prod.yml up -d

Write-Host "[4/4] Verifying cluster health status..." -ForegroundColor Green
$MaxAttempts = 30
$Attempt = 0
$AllUp = $false

while ($Attempt -lt $MaxAttempts) {
    $Attempt++
    Write-Host "Checking health probes (attempt $Attempt/$MaxAttempts)..."
    try {
        $nginxRes = Invoke-WebRequest -Uri "http://localhost/nginx-health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        $actuatorRes = Invoke-WebRequest -Uri "http://localhost/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        
        if ($nginxRes.StatusCode -eq 200 -and $actuatorRes.Content -match '"status":"UP"') {
            $AllUp = $true
            break
        }
    } catch {}
    Start-Sleep -Seconds 3
}

if ($AllUp) {
    Write-Host "==========================================================" -ForegroundColor Green
    Write-Host " [SUCCESS] AgentHub Production Cluster is Online & Healthy!" -ForegroundColor Green
    Write-Host " Image Tag (SHA):  $GitSha" -ForegroundColor Cyan
    Write-Host " Web Dashboard:    http://localhost/" -ForegroundColor Cyan
    Write-Host " REST API:         http://localhost/api" -ForegroundColor Cyan
    Write-Host " Actuator Health:  http://localhost/actuator/health" -ForegroundColor Cyan
    Write-Host " Prometheus UI:    http://127.0.0.1:9090" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "[ERROR] Services started, but healthcheck probe timed out." -ForegroundColor Red
    exit 1
}
