# AGENTS.md

Strict coding rules for future agents working on DeployGuard AI.

## Project Boundary

- DeployGuard AI is an AI-powered deployment risk and incident analysis platform.
- The platform will correlate GitHub PRs, CI/CD results, deployment events, and application logs.
- NVIDIA Nemotron 3 Ultra support is planned for future incident summaries.
- Do not implement business logic until explicitly requested.

## Repository Rules

- Keep the monorepo clean, minimal, and easy to inspect.
- Prefer small, focused changes over broad refactors.
- Do not add frameworks, runtimes, generated projects, or package managers unless requested.
- Do not create Spring Boot, FastAPI, Next.js, or other app code during structure-only tasks.
- Do not add secrets, API keys, tokens, private URLs, credentials, or real environment values.
- Use placeholder examples only when clearly marked as placeholders.
- Keep documentation accurate with the current implementation state.

## Code Rules

- Follow the established structure and naming conventions in this repository.
- Add tests when implementation work begins and risk warrants coverage.
- Keep domain logic separated from infrastructure and framework glue.
- Prefer explicit configuration over hidden defaults.
- Avoid speculative abstractions.

## AI Rules

- Do not call external AI services from repository code without explicit instruction.
- Do not store prompts, model keys, or provider secrets in source control.
- Treat incident and log data as sensitive.
- Redact sensitive values in examples and documentation.

## Git Hygiene

- Do not rewrite unrelated user changes.
- Do not commit generated dependency folders.
- Do not commit local environment files such as `.env`.
- Summarize created or changed files after each task.
