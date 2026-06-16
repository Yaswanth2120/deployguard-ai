#!/usr/bin/env bash
set -euo pipefail

AI_SERVICE_URL="${AI_SERVICE_URL:-http://127.0.0.1:8001}"

BODY_FILE="$(mktemp)"
trap 'rm -f "$BODY_FILE"' EXIT

fail() {
  echo "AI smoke test failed: $*" >&2
  exit 1
}

echo "Checking AI service health at $AI_SERVICE_URL..."
if ! curl -fsS "$AI_SERVICE_URL/health" >/dev/null; then
  fail "AI service is not running or /health is unavailable at $AI_SERVICE_URL"
fi

echo "Calling /analyze-incident..."
STATUS="$(curl -sS -o "$BODY_FILE" -w "%{http_code}" \
  -X POST "$AI_SERVICE_URL/analyze-incident" \
  -H "Content-Type: application/json" \
  -d '{
    "deployment": {
      "id": "demo-deployment-001",
      "projectId": "demo-project-001",
      "projectName": "DeployGuard Demo",
      "serviceName": "checkout-api",
      "environment": "production",
      "status": "DEPLOYED",
      "branch": "hotfix/payment-timeout",
      "commitSha": "abc123def456",
      "deployedBy": "demo.user",
      "deployedAt": "2026-06-16T12:00:00Z"
    },
    "ciRuns": [
      {
        "id": "demo-ci-001",
        "provider": "github-actions",
        "status": "FAILED",
        "commitSha": "abc123def456",
        "durationSeconds": 418,
        "failedTests": 7,
        "createdAt": "2026-06-16T11:55:00Z"
      }
    ],
    "logs": [
      {
        "id": "demo-log-001",
        "serviceName": "checkout-api",
        "level": "ERROR",
        "message": "Payment authorization latency exceeded threshold after production deployment",
        "timestamp": "2026-06-16T12:03:00Z",
        "createdAt": "2026-06-16T12:03:05Z"
      }
    ],
    "riskScore": 100,
    "riskLevel": "HIGH"
  }')" || fail "request to /analyze-incident failed"

if [ "$STATUS" -lt 200 ] || [ "$STATUS" -gt 299 ]; then
  echo "Response body:" >&2
  cat "$BODY_FILE" >&2
  echo >&2
  fail "/analyze-incident returned HTTP $STATUS"
fi

for field in summary likelyRootCause evidence recommendedActions severity confidence; do
  if ! grep -q "\"$field\"" "$BODY_FILE"; then
    echo "Response body:" >&2
    cat "$BODY_FILE" >&2
    echo >&2
    fail "missing required response field: $field"
  fi
done

echo "AI smoke test passed."
echo
echo "Response:"
if command -v python3 >/dev/null 2>&1; then
  python3 -m json.tool "$BODY_FILE" || cat "$BODY_FILE"
else
  cat "$BODY_FILE"
fi
echo
