# Runtime and configuration strategy

Docker Compose starts PostgreSQL, RabbitMQ, the API, AI service, and frontend with health-gated dependencies. It is for local development only; its credentials are intentional non-secret placeholders.

RabbitMQ is the durable asynchronous work queue for AI-analysis jobs and supports acknowledgement/retry policies. PostgreSQL is the source of record. Redis is intentionally deferred until cache or rate-limit requirements justify operating it.

Configuration is environment-specific and uses typed environment variables. Secrets (webhook secrets, GitHub tokens, GitHub OAuth client secret, JWT signing key, and model keys) are supplied by each deployment platform's secret manager, never committed or embedded in images. Changes to API contracts and database migrations are versioned in source control and deployed compatibly.

Authentication config: the backend needs `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and `JWT_SIGNING_KEY` (a long random string, unique per environment); the frontend needs `NEXT_PUBLIC_GITHUB_CLIENT_ID` matching the backend's client ID. The GitHub OAuth App's callback URL must match the deployed frontend origin plus `/auth/callback`. See the README's Environment Variables section for the full table.
