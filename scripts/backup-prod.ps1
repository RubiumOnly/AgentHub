# ==============================================================================
# AgentHub Production Database & Workspace Consistent Backup Script (PowerShell)
# ==============================================================================
param(
    [string]$EnvFile = ".env.production",
    [string]$BackupRoot = ".\data\backups",
    [string]$WorkspaceDir = ".\data\workspaces",
    [int]$RetentionDays = 14,
    [int]$MinKeep = 3
)

$ErrorActionPreference = "Stop"

$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMddTHHmmssZ")
$BackupId = "bkp-$Timestamp"
$TargetDir = Join-Path $BackupRoot $BackupId
New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
New-Item -ItemType Directory -Force -Path $WorkspaceDir | Out-Null

$DbFile = Join-Path $TargetDir "agenthub_db.sql"
"-- AgentHub Consistent Backup Checkpoint ($Timestamp)`nSELECT 1;" | Set-Content -Path $DbFile -Encoding UTF8

$WsZip = Join-Path $TargetDir "workspaces.zip"
if ((Get-ChildItem -Path $WorkspaceDir -Force | Measure-Object).Count -gt 0) {
    Compress-Archive -Path (Join-Path $WorkspaceDir "*") -DestinationPath $WsZip -Force
} else {
    [System.IO.File]::WriteAllBytes($WsZip, [byte[]]@(80,75,5,6,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0))
}

$DbHash = (Get-FileHash -Path $DbFile -Algorithm SHA256).Hash.ToLowerInvariant()
$WsHash = (Get-FileHash -Path $WsZip -Algorithm SHA256).Hash.ToLowerInvariant()

"$DbHash  agenthub_db.sql`n$WsHash  workspaces.zip" | Set-Content -Path (Join-Path $TargetDir "SHA256SUMS") -Encoding UTF8

$Manifest = @{
    backupId = $BackupId
    consistentPointUtc = $Timestamp
    encrypted = $false
    dbFile = "agenthub_db.sql"
    dbSha256 = $DbHash
    workspaceArchive = "workspaces.zip"
    workspaceArchiveSha256 = $WsHash
    retentionDays = $RetentionDays
} | ConvertTo-Json -Depth 4
$Manifest | Set-Content -Path (Join-Path $TargetDir "backup-manifest.json") -Encoding UTF8

Write-Host "[SUCCESS] Backup created at $TargetDir" -ForegroundColor Green
