# Testing strategy

- **Backend unit and web-slice tests:** service rules, request validation, response contracts, and persistence boundaries. Run with `mvn test`.
- **AI-service unit and endpoint tests:** schema validation and deterministic risk factors. Run with `PYTHONPATH=. pytest`.
- **Frontend checks:** ESLint and TypeScript/production build validation. Run with `npm run lint && npm run build`.
- **Integration tests:** run against Compose with PostgreSQL, RabbitMQ, and both HTTP services; cover migration startup, signed webhook idempotency, and PR-to-score correlation before deployment.
- **CI:** the three GitHub Actions workflows execute service-specific verification on pull requests and `main` pushes.
