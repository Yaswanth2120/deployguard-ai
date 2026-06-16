# Local Development Runbook

This runbook starts the complete DeployGuard AI stack locally:

- PostgreSQL and RabbitMQ with Docker Compose
- FastAPI AI service
- Spring Boot backend
- Next.js frontend
- Demo data seed script

Run commands from the repository root unless a step says to change directories.

## Prerequisites

Install these tools before starting:

- Java 21
- Maven
- Python 3.12
- Node.js and npm
- Docker Desktop
- curl

Optional but useful:

- `nc` for port checks

## Repository Structure

```text
deployguard-ai/
  backend/deployguard-api/  Spring Boot API
  ai-service/               FastAPI incident analysis service
  frontend/                 Next.js dashboard
  infra/                    Docker Compose configuration
  scripts/                  Local development scripts
```

## 1. Start Infrastructure

```bash
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

Verify containers are running:

```bash
docker ps
```

Verify ports:

```bash
nc -zv localhost 5432
nc -zv localhost 5672
```

RabbitMQ management UI:

```text
http://localhost:15672
```

Credentials:

```text
username: deployguard
password: deployguard
```

## 2. Start AI Service

Open a new terminal:

```bash
cd ai-service
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Health check from another terminal:

```bash
curl http://127.0.0.1:8001/health
```

Expected response:

```json
{"status":"UP","service":"deployguard-ai-service"}
```

`OPENROUTER_API_KEY` is optional for local development. Without it, the AI service returns a fallback incident analysis response.

## 3. Start Backend

Open a new terminal:

```bash
cd backend/deployguard-api
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=deployguard \
DB_USERNAME=deployguard \
DB_PASSWORD=deployguard \
AI_SERVICE_BASE_URL=http://localhost:8001 \
RABBITMQ_HOST=localhost \
RABBITMQ_PORT=5672 \
RABBITMQ_USERNAME=deployguard \
RABBITMQ_PASSWORD=deployguard \
mvn spring-boot:run
```

Health check from another terminal:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```json
{"status":"UP","service":"deployguard-api"}
```

## 4. Start Frontend

Open a new terminal:

```bash
cd frontend
npm install
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```

Open the frontend:

```text
http://localhost:3000
```

## 5. Seed Demo Data

With infrastructure, AI service, backend, and frontend running, seed realistic demo data:

```bash
./scripts/seed-demo.sh
```

Override the backend URL if needed:

```bash
BACKEND_URL=http://localhost:8080 ./scripts/seed-demo.sh
```

The script creates:

- one project
- one high-risk production deployment
- one failed CI run
- one `ERROR` application log
- one recalculated risk score
- one queued async AI analysis job

The script prints `PROJECT_ID`, `DEPLOYMENT_ID`, `JOB_ID`, and frontend URLs.

## 6. Run Tests And Checks

Backend:

```bash
cd backend/deployguard-api
DB_HOST=localhost \
DB_PORT=5432 \
DB_NAME=deployguard \
DB_USERNAME=deployguard \
DB_PASSWORD=deployguard \
AI_SERVICE_BASE_URL=http://localhost:8001 \
RABBITMQ_HOST=localhost \
RABBITMQ_PORT=5672 \
RABBITMQ_USERNAME=deployguard \
RABBITMQ_PASSWORD=deployguard \
mvn test
```

AI service:

```bash
cd ai-service
source .venv/bin/activate
python -m pytest
```

Frontend:

```bash
cd frontend
npm run build
```

## 7. Stop Infrastructure

Stop application processes with `Ctrl+C` in their terminals.

Stop Docker infrastructure:

```bash
docker compose -f infra/docker-compose.yml down
```

## Troubleshooting

### Port 5432 Already In Use

Check what is listening:

```bash
lsof -nP -iTCP:5432 -sTCP:LISTEN
```

Stop the conflicting local PostgreSQL process or change the Docker Compose port mapping.

### Port 5672 Already In Use

Check what is listening:

```bash
lsof -nP -iTCP:5672 -sTCP:LISTEN
```

Stop the conflicting RabbitMQ process before starting Docker Compose.

### Port 8001 Already In Use

Check what is listening:

```bash
lsof -nP -iTCP:8001 -sTCP:LISTEN
```

Stop the process or start the AI service on another port and set `AI_SERVICE_BASE_URL` for the backend.

### Port 8080 Already In Use

Check what is listening:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

Stop the existing backend process or start the backend with another port:

```bash
SERVER_PORT=8081 mvn spring-boot:run
```

### Port 3000 Already In Use

Check what is listening:

```bash
lsof -nP -iTCP:3000 -sTCP:LISTEN
```

Stop the existing frontend process or let Next.js choose another port.

### Docker Exec Format Error On Apple Silicon

The Postgres service should use:

```yaml
image: postgres:16
platform: linux/arm64
```

Recreate the container:

```bash
docker compose -f infra/docker-compose.yml down
docker compose -f infra/docker-compose.yml up -d postgres rabbitmq
```

### Python Virtual Environment Problems

Remove and recreate the virtual environment:

```bash
cd ai-service
rm -rf .venv
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

If `python3.12` is not installed, install Python 3.12 or use a compatible Python 3.11+ interpreter for local development.

### Backend Cannot Connect To PostgreSQL

Verify Postgres is running:

```bash
docker ps
nc -zv localhost 5432
```

Verify the backend environment values:

```text
DB_HOST=localhost
DB_PORT=5432
DB_NAME=deployguard
DB_USERNAME=deployguard
DB_PASSWORD=deployguard
```

If the error says `password authentication failed`, restart the backend with `DB_PASSWORD=deployguard`.

### Backend Cannot Connect To RabbitMQ

Verify RabbitMQ is running:

```bash
docker ps
nc -zv localhost 5672
```

Verify the backend environment values:

```text
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=deployguard
RABBITMQ_PASSWORD=deployguard
```

Check RabbitMQ logs:

```bash
docker logs deployguard-rabbitmq
```

### Browser CORS Errors

Confirm the backend is running and responding to preflight requests:

```bash
curl -i -X OPTIONS http://localhost:8080/api/projects \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: GET"
```

The response should include:

```text
Access-Control-Allow-Origin: http://localhost:3000
```

If it does not, restart the backend so it uses the latest compiled configuration.
