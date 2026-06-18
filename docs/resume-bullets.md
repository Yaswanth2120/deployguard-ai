# Resume Bullets

Every bullet below is truthful against the implementation. None of them claim production usage, traffic, latency, uptime, cost savings, or user counts, because the project does not measure those. Use the ones that fit the role.

## Three Concise Bullets

- Built **DeployGuard AI**, a deployment risk and incident-analysis platform (Spring Boot, FastAPI, Next.js, PostgreSQL, RabbitMQ) with a deterministic rule-based risk engine and AI-assisted incident summaries.
- Designed both **synchronous and asynchronous** AI analysis paths, using RabbitMQ and a Spring Boot consumer to decouple model calls from API requests via a tracked job lifecycle.
- Integrated **OpenRouter / NVIDIA Nemotron** for incident summarization with deterministic fallback so the platform stays fully functional with no API key.

## Five Detailed Bullets

- Architected a local-first monorepo (Spring Boot 3.5 / Java 21 backend, FastAPI AI service, Next.js 15 dashboard, PostgreSQL 16, RabbitMQ) that correlates deployments, CI runs, and application logs into a single relational model managed by Flyway migrations.
- Implemented a **deterministic risk engine** that scores deployments from explicit additive signals (failed CI, failed tests, ERROR logs, hotfix branches, production environment), capping at 100 and mapping to LOW/MEDIUM/HIGH for explainable, repeatable results independent of any model.
- Built an **asynchronous AI analysis pipeline** with RabbitMQ: the API enqueues a job and returns immediately, a Spring Boot consumer processes it through a PENDING → PROCESSING → COMPLETED/FAILED lifecycle, and clients poll job status — keeping request latency decoupled from model latency.
- Developed a **FastAPI AI service** that calls OpenRouter (NVIDIA Nemotron) with a grounded, JSON-only prompt and validates response shape, returning a **deterministic fallback** on missing key, provider error, timeout, or malformed output so the system degrades gracefully.
- Created a **Next.js / React / TypeScript dashboard** showing projects, deployments, risk levels, and AI activity, backed by documented REST APIs, reproducible demo data via a seed script, and a hosted portfolio deployment on Vercel/Railway.

## LinkedIn Project Description

> **DeployGuard AI — Deployment Risk & Incident Analysis Platform**
>
> A local-first platform that correlates deployment events, CI/CD results, and application logs to assess release risk and speed up incident triage. A deterministic, rule-based engine scores each deployment from concrete signals (failed CI, error logs, hotfix branches, production targets), while a separate FastAPI service generates AI incident summaries through OpenRouter / NVIDIA Nemotron — with a deterministic fallback so it works with no API key. Supports both synchronous analysis and an asynchronous RabbitMQ job pipeline with a tracked lifecycle.
>
> Stack: Spring Boot (Java 21), FastAPI (Python), Next.js / React / TypeScript, PostgreSQL, RabbitMQ, Flyway, Docker Compose, Railway, Vercel.
>
> Scoped honestly as a portfolio project: hosted demo available, but authentication, multi-tenancy, production observability, and production hardening are still on the roadmap.

## GitHub Repository Description

> Deployment risk & incident-analysis platform: deterministic risk engine + AI incident summaries (OpenRouter/Nemotron with fallback). Spring Boot, FastAPI, Next.js, PostgreSQL, RabbitMQ. Hosted demo on Vercel/Railway.

## STAR Examples

### System Design
- **Situation:** Deployment risk signals (CI results, logs, deployment history) were conceptually scattered and there was no single view tying a deployed commit to its risk.
- **Task:** Design a system that unifies these signals and adds both a risk assessment and an AI explanation.
- **Action:** Modeled projects, deployments, CI runs, logs, AI summaries, and jobs in PostgreSQL with Flyway migrations; put the deterministic risk engine in the Spring Boot backend and isolated model calls in a separate FastAPI service so the unreliable dependency could fail independently.
- **Result:** A clean separation where the score is explainable and model-free, the AI is optional and swappable, and the data model supports both sync and async analysis from the same context.

### Debugging
- **Situation:** Model providers don't always return clean JSON — outputs can be wrapped in code fences or contain extra text.
- **Task:** Make response parsing robust without trusting the model to be well-behaved.
- **Action:** Wrote `extract_json` to strip code fences and isolate the outermost JSON object, then `validate_response_shape` to require exactly the expected keys, raising on any mismatch so malformed responses fall through to the deterministic fallback.
- **Result:** Off-schema or fenced model output no longer breaks the pipeline; it degrades to a valid fallback with the identical response schema.

### Reliability
- **Situation:** The AI dependency is the least reliable part of the system (network, timeouts, rate limits, missing key).
- **Task:** Ensure the platform stays usable regardless of model availability.
- **Action:** Implemented `build_fallback_response` keyed to risk level, triggered on missing key, HTTP error, 30s timeout, invalid JSON, or shape mismatch; ensured AI service failures return a clear backend error in the sync path instead of crashing, and mark async jobs `FAILED` with an error message.
- **Result:** The whole project runs with no API key, and every AI failure mode has a defined, non-crashing outcome.

### Async Architecture
- **Situation:** Calling the model inline ties request latency to the model and doesn't scale.
- **Task:** Add an asynchronous path that returns immediately and can scale out.
- **Action:** Built a RabbitMQ-backed job pipeline: the API inserts a job (PENDING) and publishes a message, a Spring Boot consumer marks it PROCESSING, runs analysis, writes the summary, and marks it COMPLETED or FAILED; clients poll job status. Kept backend instances stateless so consumers can scale horizontally.
- **Result:** Analysis requests return immediately, model latency is decoupled from the API, and the design supports concurrent consumers with queue depth as backpressure.

### AI Integration
- **Situation:** AI incident summaries needed to be useful but grounded and safe to store.
- **Task:** Integrate a model provider without letting the model hallucinate or break the contract.
- **Action:** Integrated OpenRouter/Nemotron via a constrained system prompt (use only provided data, don't invent facts, return JSON with a fixed key set), requested `json_object` responses, validated shape before persisting, and kept the public response schema identical between real and fallback paths.
- **Result:** A consistent, validated incident-summary contract that callers and the database treat uniformly regardless of whether a model or the fallback produced it.
