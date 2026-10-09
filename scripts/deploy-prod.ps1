# ==============================================================================
# AgentHub Enterprise Production One-Click Deployment Script (PowerShell)
# ==============================================================================
$ErrorActionPreference = "Stop"

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting AgentHub Production Deployment (Windows)" -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

$EnvFile = ".env.production"
if (-not (Test-Path $EnvFile)) {
    Write-Host "[ERROR] Missing $EnvFile!" -ForegroundColor Red
    Write-Host "Please copy .env.production.example to .env.production and set production secrets." -ForegroundColor Yellow
    exit 1
}

Write-Host "[1/4] Checking prerequisites..." -ForegroundColor Green
try {
    docker --version | Out-Null
    docker compose version | Out-Null
} catch {
    Write-Host "[ERROR] Docker or Docker Compose is not installed or not in PATH." -ForegroundColor Red
    exit 1
}

Write-Host "[2/4] Building production container images..." -ForegroundColor Green
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
    Write-Host " Web Dashboard:    http://localhost/" -ForegroundColor Cyan
    Write-Host " REST API Docs:    http://localhost/api" -ForegroundColor Cyan
    Write-Host " Actuator Health:  http://localhost/actuator/health" -ForegroundColor Cyan
    Write-Host " Prometheus:       http://localhost/actuator/prometheus" -ForegroundColor Cyan
    Write-Host "==========================================================" -ForegroundColor Green
} else {
    Write-Host "[WARNING] Services started, but healthcheck probe timed out." -ForegroundColor Yellow
    Write-Host "Run 'docker compose -f docker-compose.prod.yml logs' for diagnostics." -ForegroundColor Yellow
}
