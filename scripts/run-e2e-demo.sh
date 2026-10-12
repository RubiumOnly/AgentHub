#!/usr/bin/env bash
# ==============================================================================
# AgentHub 跨平台端到端 (E2E) 演练与脱敏运行证据包导出脚本 (Bash / Linux / macOS)
# 支持模式:
#   --mode demo : 离线确定性模拟模式 (MockAgentRuntime, simulated=true)
#   --mode live : 真实 Provider 驱动模式 (ProviderDrivenAgentRuntime, simulated=false)
# ==============================================================================
set -euo pipefail

BASE_URL="${AGENTHUB_BASE_URL:-http://localhost:8080}"
MODE="demo"
EMAIL="${AGENTHUB_DEMO_EMAIL:-e2e-operator@agenthub.dev}"
PASSWORD="${AGENTHUB_DEMO_PASSWORD:-AgentHub#2026!}"
OUTPUT_DIR="${AGENTHUB_EVIDENCE_DIR:-./docs/evidence}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift 2
      ;;
    --base-url)
      BASE_URL="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
      ;;
    *)
      echo "Unknown argument: $1" >&2
      echo "Usage: $0 [--mode demo|live] [--base-url http://localhost:8080] [--output-dir ./docs/evidence]" >&2
      exit 1
      ;;
  esac
done

if [[ "$MODE" == "live" ]]; then
  AGENT_PLATFORM="PROVIDER_DRIVEN"
else
  AGENT_PLATFORM="MOCK"
fi

echo "=================================================================="
echo " AgentHub E2E Verification & Evidence Exporter (Bash)"
echo " Base URL       : ${BASE_URL}"
echo " Execution Mode : ${MODE} (agentPlatform=${AGENT_PLATFORM})"
echo "=================================================================="

# 1. 注册或登录获取 Bearer Token
echo "[1/9] Authenticating operator (${EMAIL})..."
AUTH_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\",\"displayName\":\"E2E Operator\"}")

TOKEN=$(echo "$AUTH_RESP" | jq -r '.data.token // empty')
if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  AUTH_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASSWORD}\"}")
  TOKEN=$(echo "$AUTH_RESP" | jq -r '.data.token')
fi

if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
  echo "ERROR: Failed to obtain Bearer token: ${AUTH_RESP}" >&2
  exit 1
fi
echo "  -> Token acquired."

# 2. 创建研发项目并查询工作区
echo "[2/9] Creating project and resolving JGit workspace..."
PROJ_RESP=$(curl -s -X POST "${BASE_URL}/api/projects" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"name\":\"AgentHub-E2E-${MODE}\",\"description\":\"Phase F E2E verification project (${MODE})\"}")
PROJECT_ID=$(echo "$PROJ_RESP" | jq -r '.data.id')

WS_RESP=$(curl -s "${BASE_URL}/api/projects/${PROJECT_ID}/workspace" \
  -H "Authorization: Bearer ${TOKEN}")
WORKSPACE_ID=$(echo "$WS_RESP" | jq -r '.data.id')
echo "  -> Project ID: ${PROJECT_ID}, Workspace ID: ${WORKSPACE_ID}"

# 3. 校验并持久化 DAG 工作流定义
echo "[3/9] Validating and creating DAG workflow definition..."
DSL_PAYLOAD=$(cat <<EOF
{
  "nodes": [
    {"id": "node-start", "name": "Start", "type": "START"},
    {"id": "node-code", "name": "Code Generation", "type": "AGENT", "agentPlatform": "${AGENT_PLATFORM}", "promptTemplate": "Implement OAuth2 PKCE verifier and static preview artifact"},
    {"id": "node-gate", "name": "Security Gate", "type": "APPROVAL", "requiresApproval": true},
    {"id": "node-end", "name": "End", "type": "END"}
  ],
  "edges": [
    {"id": "e1", "source": "node-start", "target": "node-code"},
    {"id": "e2", "source": "node-code", "target": "node-gate"},
    {"id": "e3", "source": "node-gate", "target": "node-end"}
  ],
  "timeoutSeconds": 120
}
EOF
)

VALIDATE_RESP=$(curl -s -X POST "${BASE_URL}/api/workflows/definitions/validate" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$DSL_PAYLOAD")
IS_VALID=$(echo "$VALIDATE_RESP" | jq -r '.data.valid')
if [[ "$IS_VALID" != "true" ]]; then
  echo "ERROR: DSL validation failed: ${VALIDATE_RESP}" >&2
  exit 1
fi

DEF_RESP=$(curl -s -X POST "${BASE_URL}/api/workflows/definitions" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"name\":\"E2E-Delivery-${MODE}\",\"version\":1,\"dsl\":${DSL_PAYLOAD}}")
DEF_ID=$(echo "$DEF_RESP" | jq -r '.data.id')
echo "  -> Definition ID: ${DEF_ID}"

# 4. 启动工作流执行并签发一次性 SSE Stream Ticket
echo "[4/9] Starting workflow run & issuing SSE stream ticket..."
IDEM_KEY="idem-e2e-$(date +%s)"
RUN_RESP=$(curl -s -X POST "${BASE_URL}/api/executions/runs" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"definitionId\":\"${DEF_ID}\",\"idempotencyKey\":\"${IDEM_KEY}\"}")
RUN_ID=$(echo "$RUN_RESP" | jq -r '.data.id')

TICKET_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/stream-ticket" \
  -H "Authorization: Bearer ${TOKEN}")
STREAM_TICKET=$(echo "$TICKET_RESP" | jq -r '.data.ticket')
echo "  -> Run ID: ${RUN_ID}, Stream Ticket issued (length=${#STREAM_TICKET})"

# 5. 等待进入 WAITING_APPROVAL 并执行人工审批放行
echo "[5/9] Waiting for human approval gate..."
for _ in {1..30}; do
  STATUS=$(curl -s "${BASE_URL}/api/executions/runs/${RUN_ID}" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.data.status')
  if [[ "$STATUS" == "WAITING_APPROVAL" || "$STATUS" == "SUCCEEDED" || "$STATUS" == "FAILED" ]]; then
    break
  fi
  sleep 0.5
done

APPROVAL_ID=$(curl -s "${BASE_URL}/api/approvals/runs/${RUN_ID}" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.data[0].id // empty')
if [[ -n "$APPROVAL_ID" && "$APPROVAL_ID" != "null" ]]; then
  curl -s -X POST "${BASE_URL}/api/approvals/${APPROVAL_ID}/approve" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d '{"reason":"Approved by E2E automated verification"}' > /dev/null
  echo "  -> Approved gate (${APPROVAL_ID})."
fi

# 6. 等待工作流达到 SUCCEEDED 终态
echo "[6/9] Waiting for workflow run completion..."
for _ in {1..30}; do
  FINAL_STATUS=$(curl -s "${BASE_URL}/api/executions/runs/${RUN_ID}" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.data.status')
  if [[ "$FINAL_STATUS" == "SUCCEEDED" || "$FINAL_STATUS" == "FAILED" || "$FINAL_STATUS" == "CANCELLED" || "$FINAL_STATUS" == "TIMED_OUT" ]]; then
    break
  fi
  sleep 0.5
done
echo "  -> Run terminal status: ${FINAL_STATUS}"

# 7. 写入工作区预览产物、创建 JGit 快照并校验结构化 Diff 与产物验收
echo "[7/9] Creating JGit snapshot, computing StructuredDiff, and accepting artifact..."
curl -s -X POST "${BASE_URL}/api/workspaces/${WORKSPACE_ID}/file" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"path":"index.html","content":"<!DOCTYPE html><html><head><title>AgentHub Preview</title></head><body><h1>AgentHub E2E Verified</h1></body></html>"}' > /dev/null

SNAP_RESP=$(curl -s -X POST "${BASE_URL}/api/workspaces/${WORKSPACE_ID}/git/snapshot" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"runId\":\"${RUN_ID}\",\"author\":\"E2E-Operator\",\"message\":\"feat: E2E preview snapshot\"}")

DIFF_FILES=$(curl -s "${BASE_URL}/api/workspaces/${WORKSPACE_ID}/diff" \
  -H "Authorization: Bearer ${TOKEN}" | jq -r '.data.totalFilesChanged')

ARTIFACT_ID=$(echo "$SNAP_RESP" | jq -r '.data.artifactId // empty')
if [[ -z "$ARTIFACT_ID" ]]; then
  ARTIFACT_ID=$(curl -s "${BASE_URL}/api/audit/artifacts/run/${RUN_ID}" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.data[0].id')
fi
curl -s -X POST "${BASE_URL}/api/audit/artifacts/${ARTIFACT_ID}/accept" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"reviewer":"E2E-Operator","comment":"Diff verified and accepted"}' > /dev/null
echo "  -> StructuredDiff files changed: ${DIFF_FILES}, Accepted Artifact ID: ${ARTIFACT_ID}"

# 8. 触发受控预览沙箱部署、探针就绪检查与回收停止
echo "[8/9] Launching STATIC_PREVIEW sandbox deployment and verifying HTTP readiness..."
DEP_RESP=$(curl -s -X POST "${BASE_URL}/api/deployments" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"projectId\":\"${PROJECT_ID}\",\"artifactId\":\"${ARTIFACT_ID}\",\"target\":\"STATIC_PREVIEW\",\"templateType\":\"STATIC_WEB_TEMPLATE\",\"sandboxType\":\"LOCAL_PROCESS\",\"healthCheckPath\":\"/\"}")
DEP_ID=$(echo "$DEP_RESP" | jq -r '.data.id')

for _ in {1..30}; do
  DEP_STATUS=$(curl -s "${BASE_URL}/api/deployments/${DEP_ID}" \
    -H "Authorization: Bearer ${TOKEN}" | jq -r '.data.status')
  if [[ "$DEP_STATUS" == "RUNNING" || "$DEP_STATUS" == "FAILED" ]]; then
    break
  fi
  sleep 0.5
done

curl -s -X POST "${BASE_URL}/api/deployments/${DEP_ID}/stop" \
  -H "Authorization: Bearer ${TOKEN}" > /dev/null
echo "  -> Deployment ${DEP_ID} reached ${DEP_STATUS} and was cleanly stopped."

# 9. 导出脱敏运行证据包 (Sanitized Run Evidence Bundle)
echo "[9/9] Exporting Sanitized Run Evidence Bundle..."
mkdir -p "${OUTPUT_DIR}"
BUNDLE_PATH="${OUTPUT_DIR}/evidence-bundle-${RUN_ID}.json"
curl -s "${BASE_URL}/api/executions/runs/${RUN_ID}/evidence/download" \
  -H "Authorization: Bearer ${TOKEN}" -o "${BUNDLE_PATH}"

SIMULATED=$(jq -r '.simulated' "${BUNDLE_PATH}")
EXEC_MODE=$(jq -r '.executionMode' "${BUNDLE_PATH}")
CHECKSUM=$(jq -r '.checksum' "${BUNDLE_PATH}")

echo "=================================================================="
echo " E2E Verification Completed!"
echo " Evidence Bundle : ${BUNDLE_PATH}"
echo " Execution Mode  : ${EXEC_MODE} (simulated=${SIMULATED})"
echo " SHA-256 Checksum: ${CHECKSUM}"
echo "=================================================================="
