#!/usr/bin/env bash
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"

request() {
  local method="$1"
  local path="$2"
  local data="${3:-}"
  local body_file
  local status

  body_file="$(mktemp)"

  if [ -n "$data" ]; then
    status="$(curl -sS -o "$body_file" -w "%{http_code}" \
      -X "$method" "$BACKEND_URL$path" \
      -H "Content-Type: application/json" \
      -d "$data")" || {
        rm -f "$body_file"
        echo "Request failed: $method $BACKEND_URL$path" >&2
        exit 1
      }
  else
    status="$(curl -sS -o "$body_file" -w "%{http_code}" \
      -X "$method" "$BACKEND_URL$path")" || {
        rm -f "$body_file"
        echo "Request failed: $method $BACKEND_URL$path" >&2
        exit 1
      }
  fi

  if [ "$status" -lt 200 ] || [ "$status" -gt 299 ]; then
    echo "Backend returned HTTP $status for $method $BACKEND_URL$path" >&2
    cat "$body_file" >&2
    echo >&2
    rm -f "$body_file"
    exit 1
  fi

  cat "$body_file"
  rm -f "$body_file"
}

json_field() {
  local field="$1"
  sed -n "s/.*\"$field\":\"\([^\"]*\)\".*/\1/p" | head -n 1
}

echo "Checking backend at $BACKEND_URL..."
if ! curl -fsS "$BACKEND_URL/api/health" >/dev/null; then
  echo "Backend is not running or is not reachable at $BACKEND_URL." >&2
  echo "Start the backend, then rerun: BACKEND_URL=$BACKEND_URL ./scripts/seed-demo.sh" >&2
  exit 1
fi

NOW="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
STAMP="$(date -u +"%Y%m%d%H%M%S")"
COMMIT_SHA="demo-${STAMP}-hotfix"

echo "Creating demo project..."
PROJECT_RESPONSE="$(request POST "/api/projects" "{
  \"name\": \"DeployGuard Demo ${STAMP}\",
  \"githubRepoUrl\": \"https://github.com/example/deployguard-demo\",
  \"serviceName\": \"checkout-api\"
}")"
PROJECT_ID="$(printf "%s" "$PROJECT_RESPONSE" | json_field "id")"

if [ -z "$PROJECT_ID" ]; then
  echo "Could not read project id from backend response:" >&2
  echo "$PROJECT_RESPONSE" >&2
  exit 1
fi

echo "Creating high-risk production deployment..."
DEPLOYMENT_RESPONSE="$(request POST "/api/deployments" "{
  \"projectId\": \"$PROJECT_ID\",
  \"commitSha\": \"$COMMIT_SHA\",
  \"branch\": \"hotfix/payment-timeout\",
  \"environment\": \"production\",
  \"status\": \"DEPLOYED\",
  \"deployedBy\": \"demo.user\",
  \"deployedAt\": \"$NOW\"
}")"
DEPLOYMENT_ID="$(printf "%s" "$DEPLOYMENT_RESPONSE" | json_field "id")"

if [ -z "$DEPLOYMENT_ID" ]; then
  echo "Could not read deployment id from backend response:" >&2
  echo "$DEPLOYMENT_RESPONSE" >&2
  exit 1
fi

echo "Creating failed CI run..."
request POST "/api/ci-runs" "{
  \"projectId\": \"$PROJECT_ID\",
  \"commitSha\": \"$COMMIT_SHA\",
  \"provider\": \"github-actions\",
  \"status\": \"FAILED\",
  \"durationSeconds\": 418,
  \"failedTests\": 7
}" >/dev/null

echo "Creating ERROR application log..."
request POST "/api/logs" "{
  \"projectId\": \"$PROJECT_ID\",
  \"deploymentId\": \"$DEPLOYMENT_ID\",
  \"serviceName\": \"checkout-api\",
  \"level\": \"ERROR\",
  \"message\": \"Payment authorization latency exceeded threshold after hotfix deployment\",
  \"timestamp\": \"$NOW\"
}" >/dev/null

echo "Recalculating deployment risk score..."
RISK_RESPONSE="$(request POST "/api/deployments/$DEPLOYMENT_ID/risk-score/recalculate")"
RISK_SCORE="$(printf "%s" "$RISK_RESPONSE" | sed -n 's/.*"riskScore":\([0-9][0-9]*\).*/\1/p' | head -n 1)"
RISK_LEVEL="$(printf "%s" "$RISK_RESPONSE" | json_field "riskLevel")"

echo "Queueing async AI analysis job..."
JOB_RESPONSE="$(request POST "/api/deployments/$DEPLOYMENT_ID/ai-analysis/jobs")"
JOB_ID="$(printf "%s" "$JOB_RESPONSE" | json_field "id")"

if [ -z "$JOB_ID" ]; then
  echo "Could not read AI analysis job id from backend response:" >&2
  echo "$JOB_RESPONSE" >&2
  exit 1
fi

cat <<EOF

Demo seed complete.

PROJECT_ID=$PROJECT_ID
DEPLOYMENT_ID=$DEPLOYMENT_ID
JOB_ID=$JOB_ID
RISK_SCORE=${RISK_SCORE:-unknown}
RISK_LEVEL=${RISK_LEVEL:-unknown}

Frontend URLs:
- Dashboard:        $FRONTEND_URL
- Projects:         $FRONTEND_URL/projects
- Deployments:      $FRONTEND_URL/deployments
- Deployment detail: $FRONTEND_URL/deployments/$DEPLOYMENT_ID

Backend URLs:
- Project:          $BACKEND_URL/api/projects/$PROJECT_ID
- Deployment:       $BACKEND_URL/api/deployments/$DEPLOYMENT_ID
- AI job:           $BACKEND_URL/api/ai-analysis/jobs/$JOB_ID
EOF
