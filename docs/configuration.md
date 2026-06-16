# Configuration

DeployGuard AI is configured with local environment variables. Example values in `.env.example` files are safe for local development only and must be reviewed before any hosted deployment.

## Environment Example Files

```text
backend/deployguard-api/.env.example
ai-service/.env.example
frontend/.env.example
```

Copy an example file to `.env` only for local development:

```bash
cp backend/deployguard-api/.env.example backend/deployguard-api/.env
cp ai-service/.env.example ai-service/.env
cp frontend/.env.example frontend/.env
```

The `.env` files are ignored by Git and must not be committed.

## Backend Variables

The backend connects to PostgreSQL, RabbitMQ, and the AI service.

```bash
DB_HOST=localhost
DB_PORT=5432
DB_NAME=deployguard
DB_USERNAME=deployguard
DB_PASSWORD=deployguard
AI_SERVICE_BASE_URL=http://localhost:8001
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=deployguard
RABBITMQ_PASSWORD=deployguard
SERVER_PORT=8080
```

Local Docker Compose credentials such as `deployguard/deployguard` are safe examples for local development only. In hosted environments, database and RabbitMQ passwords are secrets and belong in the platform secret manager.

## AI Service Variables

The AI service can run in fallback mode without an OpenRouter key.

```bash
OPENROUTER_API_KEY=
OPENROUTER_MODEL=nvidia/nemotron-3-ultra-550b-a55b:free
OPENROUTER_BASE_URL=https://openrouter.ai/api/v1
OPENROUTER_TIMEOUT_SECONDS=30
```

`OPENROUTER_API_KEY` is a secret. Keep it only in a local `.env` file or hosted secret manager. Never add a real key to `.env.example`, README files, scripts, code, or Git history.

Model identifiers and OpenRouter availability can change. Confirm the current model identifier in OpenRouter before relying on a specific model for demos or hosted environments.

## Frontend Variables

The frontend needs the backend API base URL:

```bash
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080
```

Variables prefixed with `NEXT_PUBLIC_` are exposed to browser code. Do not put secrets in frontend environment variables.

## Safe Examples Versus Secrets

Safe local examples:

- `localhost` hostnames
- local ports such as `5432`, `5672`, `8001`, `8080`, and `3000`
- local Docker Compose usernames and passwords from this repository
- public URLs such as `https://openrouter.ai/api/v1`

Secrets:

- OpenRouter API keys
- production database passwords
- production RabbitMQ passwords
- hosted platform tokens
- webhook signing secrets

## Local Startup With Environment Variables

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
SERVER_PORT=8080 \
mvn spring-boot:run
```

AI service:

```bash
cd ai-service
source .venv/bin/activate
python -m uvicorn app.main:app --host 127.0.0.1 --port 8001
```

Frontend:

```bash
cd frontend
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080 npm run dev
```
