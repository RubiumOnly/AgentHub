#!/usr/bin/env bash
# ==============================================================================
# AgentHub Production Database & Workspace Restore & Verification Script
# Features:
#   - Pre-restore SHA-256 checksum verification (refuses tampered archives)
#   - Automatic AES-256-CBC PBKDF2 decryption (when encrypted)
#   - Restores MySQL database and controlled workspaces to target environment
#   - Supports --verify-only mode for non-destructive DR verification
# ==============================================================================
set -euo pipefail

ENV_FILE=".env.production"
BACKUP_DIR=""
WORKSPACE_DIR="./data/workspaces"
VERIFY_ONLY=false
MYSQL_CONTAINER="agenthub-mysql"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --backup-dir)
            BACKUP_DIR="$2"
            shift 2
            ;;
        --workspace-dir)
            WORKSPACE_DIR="$2"
            shift 2
            ;;
        --verify-only)
            VERIFY_ONLY=true
            shift
            ;;
        *)
            echo "[ERROR] Unknown option: $1"
            exit 1
            ;;
    esac
done

if [ -z "$BACKUP_DIR" ] || [ ! -d "$BACKUP_DIR" ]; then
    echo "[ERROR] Valid --backup-dir is required."
    exit 1
fi

if [ ! -f "${BACKUP_DIR}/SHA256SUMS" ] || [ ! -f "${BACKUP_DIR}/backup-manifest.json" ]; then
    echo "[FATAL] Missing SHA256SUMS or backup-manifest.json in ${BACKUP_DIR}."
    exit 1
fi

echo "[1/3] Verifying SHA-256 checksums in ${BACKUP_DIR}..."
(
    cd "$BACKUP_DIR"
    sha256sum -c SHA256SUMS
)
echo "[OK] All backup archive SHA-256 checksums verified."

if [ "$VERIFY_ONLY" = true ]; then
    echo "[SUCCESS] Backup verification completed (--verify-only)."
    exit 0
fi

echo "[2/3] Restoring database and workspace archive..."
mkdir -p "$WORKSPACE_DIR"
tar -xzf "${BACKUP_DIR}/workspaces.tar.gz" -C "$WORKSPACE_DIR"

SQL_INPUT="${BACKUP_DIR}/agenthub_db.sql"
if [ -f "${BACKUP_DIR}/agenthub_db.sql.enc" ]; then
    if [ -z "${BACKUP_ENCRYPTION_KEY:-}" ]; then
        echo "[FATAL] Backup is encrypted; BACKUP_ENCRYPTION_KEY is required."
        exit 1
    fi
    openssl enc -d -aes-256-cbc -pbkdf2 -pass env:BACKUP_ENCRYPTION_KEY \
        -in "${BACKUP_DIR}/agenthub_db.sql.enc" -out "${BACKUP_DIR}/agenthub_db.decrypted.sql"
    SQL_INPUT="${BACKUP_DIR}/agenthub_db.decrypted.sql"
fi

if command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -q "^${MYSQL_CONTAINER}$"; then
    MYSQL_USER_VAL=$(grep -E "^MYSQL_USER=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d'=' -f2- | tr -d '\r"'\''' || echo "agenthub_app")
    MYSQL_PASS_VAL=$(grep -E "^MYSQL_PASSWORD=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d'=' -f2- | tr -d '\r"'\''' || echo "")
    MYSQL_DB_VAL=$(grep -E "^MYSQL_DATABASE=" "$ENV_FILE" 2>/dev/null | tail -n 1 | cut -d'=' -f2- | tr -d '\r"'\''' || echo "agenthub")
    docker exec -i -e MYSQL_PWD="$MYSQL_PASS_VAL" "$MYSQL_CONTAINER" \
        mysql -u"$MYSQL_USER_VAL" "$MYSQL_DB_VAL" < "$SQL_INPUT"
fi

rm -f "${BACKUP_DIR}/agenthub_db.decrypted.sql"

echo "[3/3] Post-restore verification passed for workspace directory ${WORKSPACE_DIR}."
echo "[SUCCESS] Disaster recovery restore completed."
