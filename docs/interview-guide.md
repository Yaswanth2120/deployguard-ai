# Interview Guide

This guide is written to match the actual implementation. Every claim here can be checked against the source in `backend/`, `ai-service/`, and `frontend/`. It avoids any metric the project does not measure (no latency numbers, uptime, traffic, cost savings, or user counts) and any capability the project does not have (no multi-tenancy, distributed tracing, or production-grade SaaS deployment).

## 30-Second Explanation

> DeployGuard AI is a deployment risk and incident-analysis platform. It pulls together deployment events, CI results, and application logs, scores each deployment with a deterministic risk engine, and generates an AI incident summary through a separate FastAPI service. If the model is unavailable, it falls back to a deterministic summary so the platform still works without an API key. It's a Spring Boot backend, a FastAPI AI service, a Next.js dashboard, PostgreSQL, and RabbitMQ for async work.

## Two-Minute Explanation

> The problem is that when a deployment breaks something, the evidence is scattered — CI results in one place, deployment history in another, logs somewhere else — and engineers reconstruct it by hand under pressure.
>
> DeployGuard AI brings those signals into one PostgreSQL data model: projects, deployments, CI runs, and application logs. On top of that I built two things.
>
> First, a deterministic risk engine. For a given deployment it looks at related CI runs, linked error logs, the branch name, and the environment, and adds up explicit points: a failed CI run is +30, failed tests +20, an ERROR log +30, a hotfix branch +10, production +10, capped at 100 and mapped to LOW/MEDIUM/HIGH. It's deterministic on purpose — the same inputs always give the same score, and you can explain exactly why.
>
> Second, AI incident analysis. The Spring Boot backend gathers the deployment context and calls a FastAPI service, which calls OpenRouter using an NVIDIA Nemotron model. It asks for JSON only and validates the shape — summary, likely root cause, evidence, recommended actions, severity, confidence. If the key is missing, the request times out, OpenRouter errors, or the JSON doesn't match, it returns a deterministic fallback keyed to the risk level instead of failing.
>
> There are two ways to run analysis: synchronous, where the backend calls the AI service inline and returns the summary; and asynchronous, where the backend creates a job, publishes to RabbitMQ, and a consumer processes it while the client polls for status. A Next.js dashboard shows projects, deployments, risk, and AI activity.
>
> It runs locally with Docker Compose and also has a hosted portfolio demo on Vercel/Railway. There's no multi-tenancy, distributed tracing, or production-grade SaaS hardening yet; those are roadmap items.

## Five-Minute Architecture Walkthrough

1. **Entry points.** A developer or operator uses the Next.js dashboard, or an API client calls the Spring Boot backend directly. The frontend reads `NEXT_PUBLIC_API_BASE_URL` to find the backend.

2. **Backend domain.** Spring Boot owns the core data: `projects`, `deployments`, `ci_runs`, `application_logs`, `ai_incident_summaries`, and `ai_analysis_jobs`. Each is created by a Flyway migration (`V2`–`V7`, with `V1` as init). The backend validates requests, persists through JPA, and exposes REST endpoints under `/api`.

3. **Risk scoring.** `POST /api/deployments/{id}/risk-score/recalculate` runs `RiskScoringService`. It loads the deployment, finds CI runs by the same project and commit SHA, and checks linked logs. It sums the rule-based points, caps at 100, derives the level, and saves the score and level back onto the deployment. No model is involved — this is fully deterministic.

4. **Synchronous AI analysis.** `POST /api/deployments/{id}/ai-analysis` loads the deployment, project, related CI runs, and logs, builds a context payload, and calls the FastAPI service's `/analyze-incident` endpoint inline. The result is stored in `ai_incident_summaries` and returned to the caller in the same request.

5. **The AI service.** FastAPI receives the context. If `OPENROUTER_API_KEY` is set, it calls OpenRouter's chat-completions endpoint with a system prompt that constrains the model to the provided data and requires JSON-only output with a fixed set of keys. It parses and validates the response (`validate_response_shape` requires exactly the expected keys). On any failure — missing key, HTTP error, timeout, invalid JSON, shape mismatch — it returns `build_fallback_response`, which is a deterministic summary chosen by the request's `riskLevel`.

6. **Asynchronous AI analysis.** `POST /api/deployments/{id}/ai-analysis/jobs` validates the deployment, inserts an `ai_analysis_jobs` row with status `PENDING`, publishes an `AiAnalysisJobMessage` to RabbitMQ, and returns the job immediately. A Spring Boot consumer (`AiAnalysisJobWorker`) receives the message, marks the job `PROCESSING`, loads the same context, calls the AI service, writes the summary, and marks the job `COMPLETED`. If anything fails, it marks the job `FAILED` and stores an error message. Clients poll `GET /api/ai-analysis/jobs/{jobId}`.

7. **Frontend.** The dashboard (`frontend/app/page.tsx`) aggregates projects, deployments, AI jobs, and summaries. There are dedicated project list, deployment list, and deployment detail pages, with risk and status badges.

## Design Rationale

### Why Spring Boot (backend)
Mature, well-understood framework for REST APIs with first-class validation, dependency injection, JPA persistence, Flyway integration, and RabbitMQ support. It lets the domain logic (risk scoring, job orchestration) stay separated from the framework glue, and it's a common production stack so the choices are defensible in a real team.

### Why FastAPI (AI service)
The model-facing work is naturally Python, where the HTTP and validation ecosystem for talking to model providers is strongest. FastAPI gives lightweight request validation through pydantic and simple JSON APIs. Keeping it as a separate service means the AI dependency can fail, time out, or be swapped without touching the core backend.

### Why PostgreSQL
The data is relational — deployments belong to projects, CI runs and logs link to commits and deployments, jobs and summaries link to deployments. PostgreSQL gives reliable relational storage with UUIDs and proper timestamp types, which fits these relationships better than a document store.

### Why Flyway
Schema changes need to be explicit, ordered, and repeatable across local and CI runs. Flyway versioned migrations (`V1`–`V7`) make the schema history reviewable in source control rather than applied by hand or auto-generated at runtime.

### Why RabbitMQ
Async AI analysis needs to be decoupled from the request that triggers it. RabbitMQ is a straightforward message broker that does this without pulling in heavier event-streaming infrastructure. It also sets up horizontal scaling: multiple consumers can process jobs concurrently, and queue depth provides natural backpressure when the model provider is slow.

### Why OpenRouter / NVIDIA Nemotron
OpenRouter provides a single provider-facing API for model calls, which keeps the integration simple and lets the specific model be configured rather than hard-coded. Nemotron is the configured model for incident-style summarization. Because model identifiers and free-tier availability change, the model is set via `OPENROUTER_MODEL` and the response schema is validated independently of which model produced the text.

## Sync vs. Async Trade-offs

| | Synchronous | Asynchronous |
| --- | --- | --- |
| Endpoint | `POST /api/deployments/{id}/ai-analysis` | `POST /api/deployments/{id}/ai-analysis/jobs` |
| Response | The saved summary, in the same request | A job record, immediately |
| Client model | Wait for the model/fallback | Poll `GET /api/ai-analysis/jobs/{jobId}` |
| Pros | Simple; one round trip; good for immediate needs | Returns fast; decoupled; scalable via more consumers; absorbs model latency |
| Cons | Ties the client request to model latency | Eventual result; client must poll; more moving parts |

The honest summary: synchronous is simpler and best when a caller needs the answer now and can wait for the model. Asynchronous is better for longer analysis and future scale, at the cost of polling and more infrastructure.

## Fallback Design

The fallback lives in the AI service (`build_fallback_response`) so the *entire* analysis path degrades gracefully, whether it was called synchronously or by the worker. It triggers when:

- `OPENROUTER_API_KEY` is missing,
- OpenRouter returns an HTTP error (e.g. 401/403/429),
- the request times out (30s),
- the model returns text that isn't valid JSON, or
- the JSON doesn't contain exactly the expected keys.

The fallback returns a deterministic summary, root cause, recommended actions, severity, and confidence selected by the deployment's `riskLevel` (`HIGH` / `MEDIUM` / `LOW`). The response schema is identical to a real model response, so callers and the database don't care which path produced it. This is what lets the whole project run with no API key.

## Job Lifecycle

`ai_analysis_jobs` rows move through four string states defined in `AiAnalysisJobStatus`:

1. **PENDING** — created when the async endpoint inserts the job and publishes the RabbitMQ message.
2. **PROCESSING** — set by the worker when it receives the message and starts work.
3. **COMPLETED** — set after the worker writes the `ai_incident_summaries` row; `completed_at` is recorded.
4. **FAILED** — set if the worker or a downstream call fails; an `error_message` is stored.

Transient AI-service failures (`AiServiceUnavailableException`) are retried in the worker with exponential backoff, 3 attempts by default (`AI_ANALYSIS_MAX_ATTEMPTS`). Each attempt increments `attempts`. When retries run out, `AiAnalysisJobFailureRecoverer` marks the job `FAILED` ("Failed after N attempt(s): ...") and rejects the message to `deployment.ai.analysis.dlq`. Permanent errors fail at once and are not retried. Redelivery of a `COMPLETED` job is a no-op.

## Risk Scoring Logic

The rules live in `RiskRules` (Java) and `risk_rules.py` (Python). Both are contract-tested against `shared/risk-rules.json`:

- +30 if any related CI run (same project + commit SHA) has status `FAILED`
- +20 if any related CI run has more than zero failed tests
- +30 if any log linked to the deployment has level `ERROR` or `FATAL`
- +10 if the branch name contains `hotfix` (case-insensitive)
- +10 if the environment is `production` or `prod`
- score capped at 100
- level: `LOW` ≤ 30, `MEDIUM` 31–70, `HIGH` > 70

It's deterministic and additive by design: explainable, testable, and independent of the AI service.

## Scaling Strategy

These are the intended scaling levers, described honestly as design intent rather than measured results:

- **More RabbitMQ consumers** to process async AI jobs concurrently as volume grows.
- **Queue depth as backpressure** when OpenRouter or the AI service is slow.
- **Stateless backend instances** (state lives in PostgreSQL and RabbitMQ), which supports horizontal scaling.
- **Database indexes** for common lookups (deployments by project, CI runs by project/commit, logs by deployment, jobs by deployment) as data grows.
- **Treating OpenRouter rate limits and model quotas as first-class capacity limits.**
- **Caching** (Redis is on the roadmap) for repeated dashboard reads, though it is not implemented.

## Failure Scenarios

- **AI service down (sync):** the backend returns a clear error for that request; it does not crash.
- **AI service / model failure (either path):** the AI service returns a deterministic fallback; analysis still completes.
- **OpenRouter 401/403/429 or timeout:** caught in the AI service, fallback returned.
- **Model returns malformed or off-schema JSON:** rejected by shape validation, fallback returned.
- **Worker fails mid-job:** the job is marked `FAILED` with an error message; no partial summary is silently kept.
- **RabbitMQ unavailable when publishing:** publishing raises `AiAnalysisJobPublishException` so the failure surfaces rather than being swallowed.

## Known Limitations

- Authentication exists (GitHub OAuth + PKCE); per-project authorization does not.
- No multi-tenancy.
- Hosted portfolio demo exists, but there is no production-grade SaaS deployment.
- No distributed tracing. The AI service exposes Prometheus metrics; there are no dashboards or alerts.
- LLM eval numbers are unrecorded until the eval runs with an API key. The risk model is trained on synthetic data.
- Evidence and recommended actions are stored as JSON text, not first-class relational structures.
- Redis caching is planned but not implemented.

## Future Roadmap

- Record LLM eval results (single_shot / rag / agent) and mine a real risk dataset.
- Per-project authorization, then multi-tenant organizations.
- Per-project webhook secrets and non-GitHub CI providers.
- Distributed tracing and dashboards over the existing metrics.
- Production hardening for the hosted deployment and production-grade secrets management.
- Better search, filtering, and indexing for logs and deployment history.

## The Eval Iteration Story (tell this one)

This is the strongest story in the project because every step has a number behind it. All files are in `ai-service/evals/reports/`.

1. **First run, 60 cases.** RAG scored 8/8 on the hard slice, which looked like a headline. The Wilson interval for 8/8 is 68–100%, so it proved little.
2. **Bigger set, 139 cases (55 hard).** RAG fell to the same level as single-shot, about 91% overall. The 8/8 had been luck. *Lesson: size the eval before trusting a win.*
3. **Failure analysis.** Three problems:
   - Up to 2.2% of answers failed the schema and fell back.
   - About 6% of evidence items weren't traceable to the input.
   - The agent made 0.01 tool calls per case, so it wasn't investigating.
4. **Iteration 1, reliability.** Schema-constrained decoding in vLLM, plus evidence by reference (the model cites line IDs and the server inserts the exact line).
   - Result: 100% schema-valid answers and 1.6% ungrounded evidence (0.1% for the agent), with accuracy unchanged.
   - During this step the agent's ungrounded rate briefly jumped to 15%. A trace showed it cited bare `L1` instead of `[L1]`, copying the format from tool output. That was a parser bug, not hallucination.
5. **Iteration 2, the agent.** Forcing a first tool call made it investigate, but traces showed it only searched for symptoms ("JWT", "ValueError"), never for what changed in the release.
   - Fix: a `find_changes` tool, and a prompt telling the agent to check what changed first.
   - Measured on a large-incident set where the decisive line is hidden from the prompt: agent 81% vs single-shot 33%. On the main set: 90% vs 91%.
6. **Decision.** Use single-shot when the incident fits in the context window. Use the agent for large incidents, where it pays about 1.7× the latency for a large accuracy gain.

**Likely follow-ups:**
- *"Isn't find_changes tuned to your synthetic data?"* Partly, yes. Change descriptions arrive as log lines, which flatters a keyword matcher. In production this tool would query a real change-event feed (deploy diffs, config history). The finding that the agent must look at changes, not just symptoms, still holds.
- *"Why three runs?"* Temperature 0 on vLLM is still nondeterministic because of batching. Run-to-run spread was 0.6–1.7 points, about the size of the improvements I was testing.
- *"What would you do next?"* Collect real public postmortems as an eval slice, compare embedding retrieval against BM25 with a recall@k eval, and pool repositories for the risk model.

## Likely Interviewer Questions (and Truthful Answers)

**Is the risk score AI-generated?**
There are two scores. The backend's deployment score is deterministic (`RiskRules`). The AI service's `/v1/risk-scores` returns a logistic-regression probability with per-feature contributions next to the rule baseline. The model is trained on synthetic data until a real dataset is mined, and I say so up front.

**What happens if you don't have an OpenRouter API key?**
The heuristic analyzer answers. It classifies from log/CI patterns and cites real lines as evidence. The response says `meta.source = "heuristic"` with `fallbackReason = "no_api_key"`, and the UI labels it. It is never presented as model output.

**How do you know the AI output is grounded?**
Three layers:
1. Each evidence item must share ≥60% of its content tokens with a single input line or CI record. If not, it is dropped and counted (`meta.droppedEvidence`, `deployguard_ungrounded_evidence_total`).
2. The output must match an exact key set and a closed category enum.
3. The eval harness measures hallucinated-evidence rate, injection-follow rate and secret-leak rate on a 60-case golden set.

Token overlap is a proxy: it catches fabricated evidence, not a wrong conclusion drawn from real evidence. The category-accuracy metric covers that.

**Why both sync and async? Isn't that redundant?**
They serve different callers. Sync is for "I need the summary now." Async is for decoupling and scale. Building both also demonstrates the trade-off explicitly rather than picking one and hiding the other.

**Does it retry failed jobs?**
Yes, transient AI-service failures only: exponential backoff, 3 attempts, then the job is marked FAILED and the message dead-lettered. I verified this against a live RabbitMQ by stopping the AI service: the job ended `FAILED` after 3 attempts and the DLQ held 1 message.

**Is this deployed anywhere / how much traffic does it handle?**
Yes, there is a hosted portfolio demo on Vercel/Railway. It is not a production SaaS system and there is no measured production traffic, latency, throughput, or uptime, so I won't claim those numbers.

**Is there authentication?**
Yes: GitHub OAuth with PKCE, and the backend issues a JWT that gates writes. There is no per-project authorization and no multi-tenancy. Webhooks are authenticated separately by HMAC signature.

**Why a separate AI service instead of calling the model from Spring Boot?**
Isolation. The model dependency is the least reliable part of the system, so it lives behind its own service with its own fallback. The backend can treat AI as an optional, swappable dependency, and the Python ecosystem is a better fit for model-facing code.

**How would you scale the async path?**
Run multiple RabbitMQ consumers, keep backend instances stateless, add database indexes for hot lookups, and treat OpenRouter quotas as a capacity limit. Retries and the DLQ already bound the damage from a slow provider. Next I'd add a per-provider concurrency limit.

**What would you build next?**
Run the LLM eval and publish the numbers. Mine a real revert-labeled dataset and retrain the risk model. Then add an embedding retriever with its own recall@k eval.
