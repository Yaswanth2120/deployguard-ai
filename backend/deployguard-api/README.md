# DeployGuard API

Spring Boot backend API for DeployGuard AI.

Current scope is intentionally minimal:

- Spring Boot application bootstrap
- PostgreSQL configuration
- Flyway migration support
- `GET /api/health`
- Project CRUD APIs
- Deployment APIs
- Application log ingestion APIs
- CI/CD run APIs
- Deployment risk scoring

No AI, frontend, queue, Redis, or security logic has been added yet.

## Requirements

- Java 21
- Maven
- Docker

## Run PostgreSQL

From the repository root:

```sh
cd deployguard-ai
docker compose -f infra/docker-compose.yml up -d postgres
```

PostgreSQL local development settings:

- Host: `localhost`
- Port: `5432`
- Database: `deployguard`
- Username: `deployguard`
- Password: `deployguard`

## Run Backend Locally

From the backend directory:

```sh
cd deployguard-ai/backend/deployguard-api
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=deployguard \
DB_USERNAME=deployguard \
DB_PASSWORD=deployguard \
mvn spring-boot:run
```

Flyway runs automatically at startup and applies migrations from `src/main/resources/db/migration`.

## Test Health Endpoint

With the backend running:

```sh
curl http://localhost:8080/api/health
```

Expected response:

```json
{
  "status": "UP",
  "service": "deployguard-api"
}
```

## Project API

Create a project:

```sh
curl -i -X POST http://localhost:8080/api/projects \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "DeployGuard API",
    "githubRepoUrl": "https://github.com/example/deployguard-ai",
    "serviceName": "deployguard-api"
  }'
```

List projects:

```sh
curl http://localhost:8080/api/projects
```

Get one project:

```sh
curl http://localhost:8080/api/projects/{project-id}
```

Update a project:

```sh
curl -i -X PUT http://localhost:8080/api/projects/{project-id} \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "DeployGuard API",
    "githubRepoUrl": "https://github.com/example/deployguard-ai",
    "serviceName": "deployguard-api"
  }'
```

Delete a project:

```sh
curl -i -X DELETE http://localhost:8080/api/projects/{project-id}
```

Validation failures return `400` with a JSON error response. Missing projects return `404` with a JSON error response.

## Deployment API

Create a deployment:

```sh
curl -i -X POST http://localhost:8080/api/deployments \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "{project-id}",
    "commitSha": "abc123",
    "branch": "main",
    "environment": "production",
    "status": "SUCCESS",
    "deployedBy": "yaswanth",
    "deployedAt": "2026-06-11T12:00:00Z"
  }'
```

New deployments default to:

- `riskScore`: `0`
- `riskLevel`: `LOW`

Get one deployment:

```sh
curl http://localhost:8080/api/deployments/{deployment-id}
```

List deployments for a project:

```sh
curl http://localhost:8080/api/projects/{project-id}/deployments
```

Validation failures return `400` with a JSON error response. Missing projects or deployments return `404` with a JSON error response.

## Deployment Risk Scoring

Recalculate risk for a deployment:

```sh
curl -i -X POST http://localhost:8080/api/deployments/{deployment-id}/risk-score/recalculate
```

Risk scoring rules:

- Failed CI run for the same project and commit: `+30`
- Related CI run with failed tests: `+20`
- `ERROR` logs linked to the deployment: `+30`
- Branch contains `hotfix`: `+10`
- Environment is `production` or `prod`: `+10`

Scores are capped at `100`.

Risk levels:

- `0` to `30`: `LOW`
- `31` to `70`: `MEDIUM`
- `71` to `100`: `HIGH`

## CI/CD Run API

Create a CI run:

```sh
curl -i -X POST http://localhost:8080/api/ci-runs \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "{project-id}",
    "commitSha": "abc123",
    "provider": "github-actions",
    "status": "SUCCESS",
    "durationSeconds": 120,
    "failedTests": 0
  }'
```

Get one CI run:

```sh
curl http://localhost:8080/api/ci-runs/{ci-run-id}
```

List CI runs for a project:

```sh
curl http://localhost:8080/api/projects/{project-id}/ci-runs
```

List failed CI runs for a project:

```sh
curl http://localhost:8080/api/projects/{project-id}/ci-runs/failed
```

List CI runs for a commit:

```sh
curl http://localhost:8080/api/projects/{project-id}/ci-runs/commit/{commit-sha}
```

Validation failures return `400` with a JSON error response. Missing projects or CI runs return `404` with a JSON error response.

## Application Log API

Create a log without a deployment:

```sh
curl -i -X POST http://localhost:8080/api/logs \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "{project-id}",
    "serviceName": "deployguard-api",
    "level": "INFO",
    "message": "Deployment completed",
    "timestamp": "2026-06-12T12:00:00Z"
  }'
```

Create a log linked to a deployment:

```sh
curl -i -X POST http://localhost:8080/api/logs \
  -H 'Content-Type: application/json' \
  -d '{
    "projectId": "{project-id}",
    "deploymentId": "{deployment-id}",
    "serviceName": "deployguard-api",
    "level": "ERROR",
    "message": "Request latency exceeded threshold",
    "timestamp": "2026-06-12T12:05:00Z"
  }'
```

List logs for a project:

```sh
curl http://localhost:8080/api/projects/{project-id}/logs
```

List logs for a deployment:

```sh
curl http://localhost:8080/api/deployments/{deployment-id}/logs
```

List error logs for a project:

```sh
curl http://localhost:8080/api/projects/{project-id}/logs/errors
```

Validation failures return `400` with a JSON error response. Missing projects or deployments return `404` with a JSON error response.
## AI Incident Analysis

Run the FastAPI AI service first:

```bash
cd ../../ai-service
source .venv/bin/activate
uvicorn app.main:app --reload --port 8001
```

Run the backend with the AI service URL:

```bash
AI_SERVICE_BASE_URL=http://localhost:8001 \
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=deployguard \
DB_USERNAME=deployguard \
DB_PASSWORD=deployguard \
mvn spring-boot:run
```

Analyze a deployment:

```bash
curl -i -X POST http://localhost:8080/api/deployments/{deployment-id}/ai-analysis
```

Fetch previous AI summaries for a deployment:

```bash
curl -i http://localhost:8080/api/deployments/{deployment-id}/ai-summaries
```

The backend sends deployment, related CI runs, deployment logs, `riskScore`, and `riskLevel` to the AI service. If the AI service is unavailable, the backend returns a JSON `502 Bad Gateway` error.
