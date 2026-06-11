# DeployGuard API

Spring Boot backend API for DeployGuard AI.

Current scope is intentionally minimal:

- Spring Boot application bootstrap
- PostgreSQL configuration
- Flyway migration support
- `GET /api/health`
- Project CRUD APIs

No deployment, log, CI/CD, AI, frontend, or security logic has been added yet.

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
