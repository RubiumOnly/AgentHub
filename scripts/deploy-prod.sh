#!/usr/bin/env bash
# ==============================================================================
# AgentHub Enterprise Production Deployment & Pre-Flight Security Gate Script
# Usage:
#   ./scripts/deploy-prod.sh [--env-file .env.production] [--validate-only]
# ==============================================================================
set -euo pipefail

ENV_FILE=".env.production"
VALIDATE_ONLY=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-file)
            ENV_FILE="$2"
            shift 2
            ;;
        --validate-only)
            VALIDATE_ONLY=true
            shift
            ;;
        *)
            echo "[ERROR] Unknown argument: $1"
            exit 1
            ;;
    esac
done

echo "=========================================================="
echo " AgentHub Production Pre-Flight Security & Deployment Gate"
echo "=========================================================="

if [ ! -f "$ENV_FILE" ]; then
    echo "[ERROR] Missing $ENV_FILE!"
    echo "Please copy .env.production.example to $ENV_FILE and configure strong production secrets."
    exit 1
fi

get_env_val() {
    local key="$1"
    local val
    val=$(grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d'=' -f2- | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//' -e 's/^"//' -e 's/"$//' -e "s/^'//" -e "s/'$//" | tr -d '\r' || true)
    echo "$val"
}

has_forbidden_placeholder() {
    local lower
    lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')
    if [[ "$lower" == *"changeme"* ]] || \
       [[ "$lower" == *"your-"* ]] || \
       [[ "$lower" == *"placeholder"* ]] || \
       [[ "$lower" == *"provide_strong_random"* ]] || \
       [[ "$lower" == *"secret-key-only-for-local"* ]] || \
       [[ "$lower" == *"phase1-2026-superstrong"* ]] || \
       [[ "$lower" == *"example"* ]]; then
        return 0
    fi
    return 1
}

is_weak_password() {
    local lower
    lower=$(echo "$1" | tr '[:upper:]' '[:lower:]')
    case "$lower" in
        password|123456|12345678|123456789|1234567890|admin|admin123|root|root123|redis|mysql|agenthub|qwerty)
            return 0
            ;;
        *)
            return 1
            ;;
    esac
}

VIOLATIONS=0

AUTH_SECRET=$(get_env_val "AGENTHUB_AUTH_SECRET")
if [ -z "$AUTH_SECRET" ]; then
    echo "[FATAL] AGENTHUB_AUTH_SECRET is missing or empty in $ENV_FILE."
    VIOLATIONS=$((VIOLATIONS + 1))
elif [ "${#AUTH_SECRET}" -lt 32 ]; then
    echo "[FATAL] AGENTHUB_AUTH_SECRET must be at least 32 characters (actual: ${#AUTH_SECRET})."
    VIOLATIONS=$((VIOLATIONS + 1))
elif has_forbidden_placeholder "$AUTH_SECRET" || is_weak_password "$AUTH_SECRET"; then
    echo "[FATAL] AGENTHUB_AUTH_SECRET contains forbidden placeholder or weak secret."
    VIOLATIONS=$((VIOLATIONS + 1))
fi

for DB_KEY in MYSQL_ROOT_PASSWORD MYSQL_PASSWORD; do
    DB_VAL=$(get_env_val "$DB_KEY")
    if [ -z "$DB_VAL" ]; then
        echo "[FATAL] $DB_KEY is missing or empty in $ENV_FILE."
        VIOLATIONS=$((VIOLATIONS + 1))
    elif [ "${#DB_VAL}" -lt 12 ]; then
        echo "[FATAL] $DB_KEY must be at least 12 characters (actual: ${#DB_VAL})."
        VIOLATIONS=$((VIOLATIONS + 1))
    elif has_forbidden_placeholder "$DB_VAL" || is_weak_password "$DB_VAL"; then
        echo "[FATAL] $DB_KEY contains forbidden placeholder or weak password."
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

REDIS_PASS=$(get_env_val "REDIS_PASSWORD")
if [ -n "$REDIS_PASS" ]; then
    if [ "${#REDIS_PASS}" -lt 12 ]; then
        echo "[FATAL] REDIS_PASSWORD must be at least 12 characters when set."
        VIOLATIONS=$((VIOLATIONS + 1))
    elif has_forbidden_placeholder "$REDIS_PASS" || is_weak_password "$REDIS_PASS"; then
        echo "[FATAL] REDIS_PASSWORD contains forbidden placeholder or weak password."
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
fi

SCRAPE_TOKEN=$(get_env_val "ACTUATOR_PROMETHEUS_TOKEN")
if [ -n "$SCRAPE_TOKEN" ]; then
    if [ "${#SCRAPE_TOKEN}" -lt 16 ] || has_forbidden_placeholder "$SCRAPE_TOKEN" || is_weak_password "$SCRAPE_TOKEN"; then
        echo "[FATAL] ACTUATOR_PROMETHEUS_TOKEN must be >=16 chars and must not use placeholder values."
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
fi

for API_KEY_NAME in DEEPSEEK_API_KEY ANTHROPIC_API_KEY OPENAI_API_KEY GEMINI_API_KEY; do
    API_KEY_VAL=$(get_env_val "$API_KEY_NAME")
    if [ -n "$API_KEY_VAL" ] && has_forbidden_placeholder "$API_KEY_VAL"; then
        echo "[FATAL] $API_KEY_NAME still contains placeholder value; leave blank if unused or set real secret."
        VIOLATIONS=$((VIOLATIONS + 1))
    fi
done

if [ "$VIOLATIONS" -gt 0 ]; then
    echo "[ABORT] Production pre-flight check failed with $VIOLATIONS security violation(s). Refusing to start."
    exit 1
fi

echo "[OK] Production environment file ($ENV_FILE) passed all security & secret checks."

if [ "$VALIDATE_ONLY" = true ]; then
    exit 0
fi

echo "[1/4] Checking container runtime prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is not installed."; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] Docker Compose is not installed."; exit 1; }

GIT_SHA=$(git rev-parse --short HEAD 2>/dev/null || echo "1.0.0")
export IMAGE_TAG="$GIT_SHA"
echo "[2/4] Building production container images tagged with commit SHA ($IMAGE_TAG)..."
docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml build

echo "[3/4] Launching production cluster..."
docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml up -d

echo "[4/4] Verifying cluster health status..."
MAX_ATTEMPTS=30
ATTEMPT=0
ALL_UP=false

while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    ATTEMPT=$((ATTEMPT + 1))
    echo "Checking health probes (attempt $ATTEMPT/$MAX_ATTEMPTS)..."

    if curl -s http://localhost/nginx-health >/dev/null 2>&1 && \
       curl -s http://localhost/actuator/health | grep -q "UP" >/dev/null 2>&1; then
        ALL_UP=true
        break
    fi
    sleep 3
done

if [ "$ALL_UP" = true ]; then
    echo "=========================================================="
    echo " [SUCCESS] AgentHub Production Cluster is Online & Healthy!"
    echo " Image Tag (SHA):  $IMAGE_TAG"
    echo " Web Dashboard:    http://localhost/ (configure TLS via docker/nginx/agenthub-tls.conf for public prod)"
    echo " REST API:         http://localhost/api"
    echo " Actuator Health:  http://localhost/actuator/health"
    echo " Prometheus UI:    http://127.0.0.1:9090"
    echo "=========================================================="
else
    echo "[ERROR] Services started, but healthcheck probe timed out."
    docker compose --env-file "$ENV_FILE" -f docker-compose.prod.yml ps
    exit 1
fi
