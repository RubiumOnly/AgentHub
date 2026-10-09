#!/usr/bin/env bash
# ==============================================================================
# AgentHub Enterprise Production One-Click Deployment Script
# ==============================================================================
set -euo pipefail

echo "=========================================================="
echo " Starting AgentHub Production Deployment"
echo "=========================================================="

ENV_FILE=".env.production"
if [ ! -f "$ENV_FILE" ]; then
    echo "[ERROR] Missing $ENV_FILE!"
    echo "Please copy .env.production.example to .env.production and set production secrets."
    exit 1
fi

echo "[1/4] Checking prerequisites..."
command -v docker >/dev/null 2>&1 || { echo "[ERROR] Docker is not installed."; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "[ERROR] Docker Compose is not installed."; exit 1; }

echo "[2/4] Building production container images..."
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
    echo " Web Dashboard:    http://localhost/"
    echo " REST API Docs:    http://localhost/api"
    echo " Actuator Health:  http://localhost/actuator/health"
    echo " Prometheus:       http://localhost/actuator/prometheus"
    echo "=========================================================="
else
    echo "[WARNING] Services started, but healthcheck probe timed out."
    echo "Run 'docker compose -f docker-compose.prod.yml logs' for diagnostics."
fi
