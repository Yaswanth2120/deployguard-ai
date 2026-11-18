# Resume Bullets

Rule for every bullet: a number appears only if a script in this repository produced it. Numbers marked **[run first]** do not exist yet. Produce them before using the bullet. Don't estimate them.

## AI Engineer — primary bullets

- Built an **LLM incident-triage system** (FastAPI, OpenRouter/Nemotron) with four interchangeable modes: heuristic, single-shot, RAG (BM25 over past incidents and runbooks), and a tool-calling agent (log search, CI failures, knowledge-base lookup). Output uses a strict JSON contract with a closed 11-label root-cause taxonomy.
- Designed a **60-case golden evaluation set** with core, hard (misleading-keyword), ambiguous, prompt-injection, and secret-leak slices. Scoring covers category accuracy, macro-F1, evidence grounding, injection-follow rate, leak rate, p50/p95 latency, tokens and cost, plus optional LLM-as-judge. A CI gate blocks regressions.
  - Measured result: the deterministic baseline scores 85% overall and **0/8 on the hard slice**.
  - **[run first]** Fill in LLM results with `python -m evals.run --mode heuristic --mode single_shot --mode rag --mode agent`, for example "RAG raised hard-slice accuracy from 0% to X% at Y tokens/analysis".
- Hardened the LLM path for sensitive incident data. Regex redaction (tokens, keys, credentials, PII) runs before every model call and tool output. Untrusted logs are delimited to resist prompt injection. A grounding filter drops evidence the input cannot support and surfaces the drop rate as a metric. Measured on the golden set: **0% secret leak and 0% injection-follow**, heuristic path.
- Trained a **logistic-regression deployment-risk model** (IRLS in numpy, L2 tuned on validation, ordered split) with training/serving feature parity and per-feature explanations, served without ML dependencies.
  - On held-out synthetic data it beats the rule baseline: **ROC-AUC 0.79 vs 0.68, PR-AUC 0.37 vs 0.22, ECE 0.010 vs 0.074**.
  - Say "synthetic". Also built a GitHub miner that labels reverted PRs. **[run first]** mine a public repo before you claim real-data numbers.
- Made AI provenance first-class. Every summary persists source (model or heuristic), fallback reason, prompt version, tokens, latency and dropped evidence. Prometheus metrics track fallback rate, cost and ungrounded evidence, so model quality is observable in production rather than only offline.

## Platform / backend bullets

- Built a deployment-risk platform (Spring Boot 3.5 / Java 21, FastAPI, Next.js, PostgreSQL, RabbitMQ) that ingests **signed GitHub webhooks** (HMAC-SHA256, delivery-ID idempotency) for workflow runs, pull requests and deployment statuses. When a late CI failure arrives, it re-scores every deployment of that commit.
- Added **retry with exponential backoff and a dead-letter queue** to the async analysis pipeline. Only transient AI-service errors are retried. Exhausted jobs are marked FAILED with their attempt count and dead-lettered. This was verified end to end against a live RabbitMQ instance.
- Removed three drifting copies of the risk rules (Java deployment scoring, Java PR scoring, Python) in favor of one versioned spec (`shared/risk-rules.json`), enforced by contract tests in both languages.

## Interview framing

- **"Why logistic regression, not XGBoost?"** It has 14 features, needs calibrated probabilities and per-feature explanations, and must serve without ML dependencies. Gradient-boosted trees are the next experiment once real data exists. Compare them on PR-AUC and ECE.
- **"How do you know the LLM is better than rules?"** Run the hard slice, where keywords mislead and the heuristic scores 0/8. If the LLM modes don't beat that, the LLM isn't earning its cost.
- **"What stops the model from inventing evidence?"** Grounding: each evidence item must overlap ≥60% with one input line, or it is dropped and counted.

## Do not claim

- Production traffic, users, uptime, or incident-reduction percentages. None are measured.
- Real-world model accuracy. The risk model is trained on synthetic data until mined data exists.
- Any LLM eval number not taken from `evals/reports/latest.md`.
