#!/usr/bin/env bash
# ==============================================================================
# AgentHub 本地全量 CI 门禁模拟脚本 (Bash / Linux / macOS / WSL)
# 严格对齐 .github/workflows/ci.yml 中的 3 个核心 Job
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

write_section() {
    echo -e "\n${CYAN}==================================================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}==================================================================${NC}"
}

write_pass() {
    echo -e "  ${GREEN}[PASS] $1${NC}"
}

write_fail() {
    echo -e "  ${RED}[FAIL] $1${NC}"
}

START_TIME=$(date +%s)

# -------------------------------------------------------------------------
# JOB 1: Backend Tests & Architecture Gate
# -------------------------------------------------------------------------
write_section "Job 1: Backend Tests, Architecture & Debt Freeze Gate"
cd "$REPO_ROOT/agenthub-backend"
echo -e "${YELLOW}Running full backend test suite with alphabetical runOrder...${NC}"
mvn clean test
write_pass "Backend full test suite passed cleanly."

# -------------------------------------------------------------------------
# JOB 2: Frontend Build, Typecheck & Debt Freeze Gate
# -------------------------------------------------------------------------
write_section "Job 2: Frontend Build, Typecheck & Debt Freeze Gate"
cd "$REPO_ROOT/agenthub-frontend"

echo -e "${YELLOW}1. Checking Phase 0 debt freeze (zero hardcoded localhost, legacy /api/workspace/, user-1)...${NC}"
if grep -RIn -E "localhost|127\.0\.0\.1|/api/workspace/|d:/work/agenthub|\"user-1\"|'user-1'|isDemoMode\(\) \|\| !projectId|isDemoMode\(\) \|\| runId === MOCK_RUN\.id" src/; then
    write_fail "Hardcoded debt violations found in frontend src!"
    exit 1
fi
write_pass "Zero debt violations verified in frontend src."

echo -e "${YELLOW}2. Running TypeScript strict typecheck...${NC}"
npx tsc --noEmit
write_pass "TypeScript typecheck passed with 0 errors."

echo -e "${YELLOW}3. Building Next.js production bundle...${NC}"
npm run build
write_pass "Next.js production build succeeded."

# -------------------------------------------------------------------------
# JOB 3: Production Config, Compose & DR Drill Gate
# -------------------------------------------------------------------------
write_section "Job 3: Production Config, Compose & DR Drill Gate"
cd "$REPO_ROOT"

echo -e "${YELLOW}1. Verifying .env.production.example rejects deployment...${NC}"
if bash ./scripts/deploy-prod.sh --env-file .env.production.example --validate-only; then
    write_fail ".env.production.example with placeholder values must be rejected!"
    exit 1
fi
write_pass "Placeholder .env.production.example was properly rejected."

echo -e "${YELLOW}2. Generating strong CI env and testing docker-compose.prod.yml config...${NC}"
cat << 'EOF' > .env.production.local-ci
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
EOF

docker compose --env-file .env.production.local-ci -f docker-compose.prod.yml config --quiet
rm -f .env.production.local-ci
write_pass "docker-compose.prod.yml configuration syntax valid."

END_TIME=$(date +%s)
ELAPSED=$((END_TIME - START_TIME))

write_section "ALL CI GATES PASSED (Total: ${ELAPSED}s)"
echo -e "${GREEN}  Local CI simulation completed successfully! Safe to commit and push.${NC}"
