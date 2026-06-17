# Screenshot Checklist

A capture list for a portfolio or interview walkthrough. The goal is to show the real platform working end to end. Capture these against locally seeded data — run `./scripts/seed-demo.sh` first (see [local-development.md](local-development.md)) so a high-risk production deployment, a failed CI run, an ERROR log, and an async job already exist.

> Honesty note: capture real screens. Do not fabricate dashboards, URLs, or metrics. If something isn't available yet (for example, a real model summary because no API key is set, or any hosted/deployment screen), use the placeholder note for that item rather than faking it.

## Shots to Capture

| # | Screenshot | Where to find it | Notes |
| --- | --- | --- | --- |
| 1 | **Dashboard overview** | `http://localhost:3000/` | Top-level view aggregating projects, deployments, and AI activity. |
| 2 | **Project list** | `http://localhost:3000/projects` | The seeded project should appear. |
| 3 | **Deployment list** | `http://localhost:3000/deployments` | Shows deployments with status and risk badges. |
| 4 | **Deployment detail** | `http://localhost:3000/deployments/{DEPLOYMENT_ID}` | Use the `DEPLOYMENT_ID` printed by the seed script. |
| 5 | **HIGH risk score** | Deployment detail of the seeded production deployment | The seed creates a high-risk deployment; capture the HIGH risk badge/score. |
| 6 | **Failed CI run** | Deployment detail (CI section) | The seeded `FAILED` CI run. |
| 7 | **ERROR application log** | Deployment detail (logs section) | The seeded `ERROR`-level log. |
| 8 | **Real AI incident summary** | Deployment detail after triggering AI analysis with a valid `OPENROUTER_API_KEY` | PLACEHOLDER if no API key: capture the deterministic fallback summary instead and label it as fallback. See note below. |
| 9 | **Async job status** | Dashboard AI activity, or `GET /api/ai-analysis/jobs/{JOB_ID}` | Show a job in PROCESSING and/or COMPLETED state (use the `JOB_ID` from the seed script). |
| 10 | **RabbitMQ management dashboard** | `http://localhost:15672` (user/pass: `deployguard`/`deployguard`) | Show the AI analysis queue and message activity. |
| 11 | **Architecture Mermaid diagram** | [system-design.md](system-design.md) rendered (GitHub or a Markdown preview) | The high-level architecture diagram. |
| 12 | **Successful validation output** | Terminal | PLACEHOLDER: an automated E2E validation script is not on this branch yet. Until it exists, capture a passing run of the test/checks step in [local-development.md](local-development.md#6-run-tests-and-checks) and/or `./scripts/smoke-test-ai.sh` output, and replace this with the E2E output once that work is merged. |

### Note on the AI summary (item 8)
A "real" model summary requires a valid `OPENROUTER_API_KEY` and an available Nemotron model on OpenRouter. Model availability and free-tier limits change. If you can't get a live model response, capture the **deterministic fallback** summary and clearly label it as the fallback path — that is still an accurate representation of the system.

## Naming and Storing Screenshots

Store all screenshots under [docs/assets/](assets/) using this convention:

```
docs/assets/
  01-dashboard-overview.png
  02-project-list.png
  03-deployment-list.png
  04-deployment-detail.png
  05-high-risk-score.png
  06-failed-ci-run.png
  07-error-application-log.png
  08-ai-incident-summary.png        # suffix -fallback if it is the fallback path
  09-async-job-status.png
  10-rabbitmq-management.png
  11-architecture-diagram.png
  12-validation-output.png          # PLACEHOLDER until E2E validation exists
```

Guidelines:

- Use a leading two-digit number matching this list so files sort in walkthrough order.
- Use lowercase kebab-case, `.png` preferred (`.gif` is fine for short interactions like an async job updating).
- **Redact secrets** before saving: never capture a real `OPENROUTER_API_KEY`, tokens, or any non-local credential. Local Docker Compose credentials (`deployguard`/`deployguard`) are fine.
- Keep file sizes reasonable; crop to the relevant UI.
- When you embed these in docs, reference them with relative paths, e.g. `![Dashboard](assets/01-dashboard-overview.png)`.
