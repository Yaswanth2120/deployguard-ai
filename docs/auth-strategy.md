# Authentication and authorization strategy

## Implementation status

Implemented: GitHub OAuth (Authorization Code + PKCE) sign-in, backend-issued short-lived JWT `Bearer` sessions, and endpoint gating (GET is public, write endpoints require a valid token). See `backend/deployguard-api/src/main/java/com/deployguard/api/auth/` and `frontend/lib/AuthProvider.tsx`.

Not yet implemented: per-project role membership checks (the `viewer`/`operator`/`admin` roles below are aspirational — every authenticated GitHub user currently gets full write access), the GitHub/CI webhook signature verification described below, and true OIDC (GitHub's OAuth App flow does not issue an `id_token` or expose a discovery document; the backend fills that gap by minting its own signed session JWT after fetching the GitHub user profile).

## Trust boundaries

- GitHub and CI/CD providers call only webhook endpoints; browser clients never call them.
- The frontend calls user-facing API endpoints with a short-lived access token.
- The backend alone can call internal AI endpoints on the Docker network.

## GitHub webhook authentication

Each project has an encrypted-at-rest webhook secret, referenced through a secret manager rather than source control. The API reads the raw request body, calculates `HMAC-SHA-256`, and constant-time compares it with GitHub's `X-Hub-Signature-256`. It rejects missing, invalid, stale, or replayed deliveries before JSON parsing. `X-GitHub-Delivery` is persisted with a uniqueness constraint for idempotency. CI providers use their equivalent signed-delivery validation.

## Frontend authentication and authorization

Use an OIDC identity provider with Authorization Code + PKCE. The frontend receives a short-lived JWT access token and sends it as `Authorization: Bearer <token>`. The API validates issuer, audience, signature, expiry, and tenant/project claims. Roles are `viewer` (read), `operator` (ingest and trigger analysis), and `admin` (project integrations and secrets). Every project-scoped request authorizes membership before loading data.

## Service-to-service and operations

The backend uses an internal service identity for the AI service; it is not exposed through the frontend. Secrets live in deployment secret stores, are rotated, redacted from logs, and are never returned by APIs. Apply rate limits, request-size limits, audit trails for ingestion and score access, TLS in deployed environments, and structured redaction for log fields.
