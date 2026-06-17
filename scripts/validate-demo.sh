#!/usr/bin/env bash
set -uo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
AI_SERVICE_URL="${AI_SERVICE_URL:-http://127.0.0.1:8001}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"
POLL_INTERVAL_SECONDS="${POLL_INTERVAL_SECONDS:-2}"
POLL_TIMEOUT_SECONDS="${POLL_TIMEOUT_SECONDS:-60}"

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PYTHON_BIN=""
PASS_COUNT=0
FAIL_COUNT=0
FAILURES=()
TEMP_FILES=()

cleanup() {
  local file
  for file in "${TEMP_FILES[@]+"${TEMP_FILES[@]}"}"; do
    rm -f "$file"
  done
}
trap cleanup EXIT

section() {
  echo
  echo "== $1 =="
}

pass() {
  PASS_COUNT=$((PASS_COUNT + 1))
  echo "PASS $1"
}

fail() {
  FAIL_COUNT=$((FAIL_COUNT + 1))
  FAILURES+=("$1")
  echo "FAIL $1" >&2
}

finish() {
  echo
  echo "== Summary =="
  echo "PASS checks: $PASS_COUNT"
  echo "FAIL checks: $FAIL_COUNT"

  if [ "$FAIL_COUNT" -gt 0 ]; then
    echo
    echo "Failures:"
    local failure
    for failure in "${FAILURES[@]}"; do
      echo "- $failure"
    done
    echo
    echo "DeployGuard AI demo validation failed."
    exit 1
  fi

  echo
  echo "DeployGuard AI demo validation passed."
}

abort_if_failed() {
  if [ "$FAIL_COUNT" -gt 0 ]; then
    finish
  fi
}

require_command() {
  local command_name="$1"
  if command -v "$command_name" >/dev/null 2>&1; then
    pass "command available: $command_name"
  else
    fail "missing required command: $command_name"
  fi
}

detect_python() {
  if command -v python3 >/dev/null 2>&1; then
    PYTHON_BIN="python3"
    pass "command available: python3"
  elif command -v python >/dev/null 2>&1; then
    PYTHON_BIN="python"
    pass "command available: python"
  else
    fail "missing required command: python or python3"
  fi
}

request() {
  local method="$1"
  local url="$2"
  local data="${3:-}"
  local body_file
  local status

  body_file="$(mktemp)"
  TEMP_FILES+=("$body_file")

  if [ -n "$data" ]; then
    status="$(curl -sS -o "$body_file" -w "%{http_code}" \
      -X "$method" "$url" \
      -H "Content-Type: application/json" \
      -d "$data")" || return 1
  else
    status="$(curl -sS -o "$body_file" -w "%{http_code}" \
      -X "$method" "$url")" || return 1
  fi

  if [ "$status" -lt 200 ] || [ "$status" -gt 299 ]; then
    echo "HTTP $status from $method $url" >&2
    cat "$body_file" >&2
    echo >&2
    return 1
  fi

  cat "$body_file"
}

json_get() {
  local field="$1"
  "$PYTHON_BIN" -c '
import json
import sys

field = sys.argv[1]
data = json.load(sys.stdin)
value = data
for part in field.split("."):
    value = value[part]
if value is None:
    sys.exit(1)
print(value)
' "$field"
}

json_has_fields() {
  "$PYTHON_BIN" -c '
import json
import sys

data = json.load(sys.stdin)
missing = [field for field in sys.argv[1:] if field not in data or data[field] is None]
if missing:
    print(", ".join(missing))
    sys.exit(1)
' "$@"
}

json_array_min_length() {
  local minimum="$1"
  "$PYTHON_BIN" -c '
import json
import sys

minimum = int(sys.argv[1])
data = json.load(sys.stdin)
if not isinstance(data, list) or len(data) < minimum:
    sys.exit(1)
' "$minimum"
}

port_reachable() {
  local host="$1"
  local port="$2"
  "$PYTHON_BIN" -c '
import socket
import sys

host = sys.argv[1]
port = int(sys.argv[2])
with socket.create_connection((host, port), timeout=3):
    pass
' "$host" "$port" >/dev/null 2>&1
}

container_running() {
  local container_name="$1"
  docker ps \
    --filter "name=^/${container_name}$" \
    --filter "status=running" \
    --format "{{.Names}}" | grep -qx "$container_name"
}

health_check() {
  local name="$1"
  local url="$2"
  if curl -fs "$url" >/dev/null 2>&1; then
    pass "$name is reachable at $url"
  else
    fail "$name is not reachable at $url"
  fi
}

extract_seed_value() {
  local key="$1"
  sed -n "s/^${key}=//p" | tail -n 1
}

validate_positive_integer() {
  local name="$1"
  local value="$2"
  if [[ "$value" =~ ^[1-9][0-9]*$ ]]; then
    pass "$name is set to $value"
  else
    fail "$name must be a positive integer; got '$value'"
  fi
}

echo "DeployGuard AI end-to-end demo validation"
echo "Backend:  $BACKEND_URL"
echo "AI:       $AI_SERVICE_URL"
echo "Frontend: $FRONTEND_URL"
echo "Polling:  every ${POLL_INTERVAL_SECONDS}s for up to ${POLL_TIMEOUT_SECONDS}s"

section "Prerequisites"
require_command docker
require_command curl
require_command java
require_command mvn
detect_python
require_command node
require_command npm
validate_positive_integer "POLL_INTERVAL_SECONDS" "$POLL_INTERVAL_SECONDS"
validate_positive_integer "POLL_TIMEOUT_SECONDS" "$POLL_TIMEOUT_SECONDS"
abort_if_failed

section "Infrastructure"
if container_running deployguard-postgres; then
  pass "PostgreSQL container is running"
else
  fail "PostgreSQL container deployguard-postgres is not running"
fi

if container_running deployguard-rabbitmq; then
  pass "RabbitMQ container is running"
else
  fail "RabbitMQ container deployguard-rabbitmq is not running"
fi

if port_reachable 127.0.0.1 5432; then
  pass "PostgreSQL port 5432 is reachable"
else
  fail "PostgreSQL port 5432 is not reachable"
fi

if port_reachable 127.0.0.1 5672; then
  pass "RabbitMQ port 5672 is reachable"
else
  fail "RabbitMQ port 5672 is not reachable"
fi
abort_if_failed

section "Services"
health_check "AI service health" "$AI_SERVICE_URL/health"
health_check "backend health" "$BACKEND_URL/api/health"
health_check "frontend" "$FRONTEND_URL"
abort_if_failed

section "Seed Demo Data"
SEED_OUTPUT="$(BACKEND_URL="$BACKEND_URL" FRONTEND_URL="$FRONTEND_URL" "$ROOT_DIR/scripts/seed-demo.sh" 2>&1)"
SEED_STATUS=$?
echo "$SEED_OUTPUT"

if [ "$SEED_STATUS" -eq 0 ]; then
  pass "scripts/seed-demo.sh completed"
else
  fail "scripts/seed-demo.sh failed"
  abort_if_failed
fi

PROJECT_ID="$(printf "%s\n" "$SEED_OUTPUT" | extract_seed_value PROJECT_ID)"
DEPLOYMENT_ID="$(printf "%s\n" "$SEED_OUTPUT" | extract_seed_value DEPLOYMENT_ID)"
JOB_ID="$(printf "%s\n" "$SEED_OUTPUT" | extract_seed_value JOB_ID)"

if [ -n "$PROJECT_ID" ]; then
  pass "captured PROJECT_ID=$PROJECT_ID"
else
  fail "could not capture PROJECT_ID from seed output"
fi

if [ -n "$DEPLOYMENT_ID" ]; then
  pass "captured DEPLOYMENT_ID=$DEPLOYMENT_ID"
else
  fail "could not capture DEPLOYMENT_ID from seed output"
fi

if [ -n "$JOB_ID" ]; then
  pass "captured JOB_ID=$JOB_ID"
else
  fail "could not capture JOB_ID from seed output"
fi
abort_if_failed

section "Workflow Checks"
if DEPLOYMENT_RESPONSE="$(request GET "$BACKEND_URL/api/deployments/$DEPLOYMENT_ID")"; then
  pass "seeded deployment exists"
else
  fail "seeded deployment was not returned by backend"
fi

RISK_LEVEL="$(printf "%s" "$DEPLOYMENT_RESPONSE" | json_get riskLevel 2>/dev/null || true)"
RISK_SCORE="$(printf "%s" "$DEPLOYMENT_RESPONSE" | json_get riskScore 2>/dev/null || true)"

if [ "$RISK_LEVEL" = "HIGH" ]; then
  pass "seeded deployment risk level is HIGH"
else
  fail "expected risk level HIGH, got '${RISK_LEVEL:-missing}'"
fi

if [ -n "$RISK_SCORE" ]; then
  pass "seeded deployment risk score is present: $RISK_SCORE"
else
  fail "seeded deployment risk score is missing"
fi

if SYNC_RESPONSE="$(request POST "$BACKEND_URL/api/deployments/$DEPLOYMENT_ID/ai-analysis")" &&
  printf "%s" "$SYNC_RESPONSE" | json_has_fields \
  id deploymentId summary likelyRootCause evidence recommendedActions severity confidence modelName createdAt >/dev/null; then
  pass "synchronous AI analysis returned expected response schema"
else
  fail "synchronous AI analysis response is missing expected fields"
fi

if JOB_RESPONSE="$(request GET "$BACKEND_URL/api/ai-analysis/jobs/$JOB_ID")" &&
  printf "%s" "$JOB_RESPONSE" | json_has_fields id deploymentId status createdAt updatedAt >/dev/null; then
  pass "asynchronous AI analysis job exists"
else
  fail "asynchronous AI analysis job response is missing expected fields"
fi
abort_if_failed

section "Async Job Polling"
DEADLINE=$((SECONDS + POLL_TIMEOUT_SECONDS))
JOB_STATUS=""
JOB_ERROR=""

while [ "$SECONDS" -le "$DEADLINE" ]; do
  if ! JOB_RESPONSE="$(request GET "$BACKEND_URL/api/ai-analysis/jobs/$JOB_ID")"; then
    fail "could not fetch async AI analysis job $JOB_ID while polling"
    break
  fi

  JOB_STATUS="$(printf "%s" "$JOB_RESPONSE" | json_get status 2>/dev/null || true)"
  JOB_ERROR="$(printf "%s" "$JOB_RESPONSE" | json_get errorMessage 2>/dev/null || true)"

  echo "Async job $JOB_ID status: ${JOB_STATUS:-unknown}"

  if [ "$JOB_STATUS" = "COMPLETED" ]; then
    pass "async AI analysis job completed"
    break
  fi

  if [ "$JOB_STATUS" = "FAILED" ]; then
    fail "async AI analysis job failed: ${JOB_ERROR:-no error message returned}"
    break
  fi

  sleep "$POLL_INTERVAL_SECONDS"
done

if [ "$JOB_STATUS" != "COMPLETED" ] && [ "$JOB_STATUS" != "FAILED" ]; then
  fail "async AI analysis job timed out after ${POLL_TIMEOUT_SECONDS}s; last status was '${JOB_STATUS:-unknown}'"
fi
abort_if_failed

section "Stored AI Summaries"
if SUMMARY_RESPONSE="$(request GET "$BACKEND_URL/api/deployments/$DEPLOYMENT_ID/ai-summaries")" &&
  printf "%s" "$SUMMARY_RESPONSE" | json_array_min_length 1 >/dev/null; then
  pass "at least one AI incident summary is stored"
else
  fail "no AI incident summaries were stored for deployment $DEPLOYMENT_ID"
fi

finish
