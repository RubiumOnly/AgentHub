# ==============================================================================
# AgentHub Production Database & Workspace Restore Script (PowerShell)
# ==============================================================================
param(
    [Parameter(Mandatory=$true)][string]$BackupDir,
    [string]$WorkspaceDir = ".\data\workspaces",
    [switch]$VerifyOnly = $false
)

$ErrorActionPreference = "Stop"

$ManifestPath = Join-Path $BackupDir "backup-manifest.json"
$ShaPath = Join-Path $BackupDir "SHA256SUMS"
if (-not (Test-Path $ManifestPath) -or -not (Test-Path $ShaPath)) {
    throw "Missing backup-manifest.json or SHA256SUMS in $BackupDir"
}

$Manifest = Get-Content $ManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$DbPath = Join-Path $BackupDir $Manifest.dbFile
$WsPath = Join-Path $BackupDir $Manifest.workspaceArchive

$ActualDbHash = (Get-FileHash -Path $DbPath -Algorithm SHA256).Hash.ToLowerInvariant()
$ActualWsHash = (Get-FileHash -Path $WsPath -Algorithm SHA256).Hash.ToLowerInvariant()

if ($ActualDbHash -ne $Manifest.dbSha256.ToLowerInvariant()) {
    throw "Database snapshot SHA-256 mismatch! Expected $($Manifest.dbSha256), got $ActualDbHash"
}
if ($ActualWsHash -ne $Manifest.workspaceArchiveSha256.ToLowerInvariant()) {
    throw "Workspace archive SHA-256 mismatch! Expected $($Manifest.workspaceArchiveSha256), got $ActualWsHash"
}

Write-Host "[OK] Backup SHA-256 checksums verified." -ForegroundColor Green
if ($VerifyOnly) {
    exit 0
}

New-Item -ItemType Directory -Force -Path $WorkspaceDir | Out-Null
Expand-Archive -Path $WsPath -DestinationPath $WorkspaceDir -Force
Write-Host "[SUCCESS] Restore completed to $WorkspaceDir" -ForegroundColor Green
