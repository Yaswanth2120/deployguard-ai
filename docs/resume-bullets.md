# Resume Bullets

Rule for every bullet: a number appears only if a script in this repository produced it. The source of each number is given underneath it. Run results live in `ai-service/evals/reports/`.

## AI Engineer — primary bullets

- Built an **LLM incident-triage system** with four interchangeable modes: heuristic, single-shot, RAG and a tool-calling agent. It is self-hosted on vLLM (gpt-oss-120b on one A100), uses schema-constrained decoding and a closed 11-label root-cause taxonomy, and falls back to a deterministic analyzer with recorded provenance.

- Designed a **139-case incident eval plus a 12-case large-incident set**. Cases are split into core, hard (misleading-keyword), ambiguous, prompt-injection and secret-leak slices. Each mode is run 3 times and reported with Wilson 95% intervals, and a CI regression gate guards the result. The larger set showed that an early "8/8 on hard cases" was sampling luck (Wilson interval 68–100%).
  - *Source:* `evals/reports/final-gptoss.md`. The keyword baseline reaches 25% on the hard slice; gpt-oss single-shot reaches 85% and overall accuracy 91% (95% CI 85–95%).

- **Iteration 1, reliability.** Failure analysis of the first run showed up to 2.2% of answers failing the schema and 6% of evidence ungrounded. Constrained decoding and evidence-by-reference (the model cites line IDs, the server substitutes the exact text) brought schema-valid answers to **100%** and ungrounded evidence to **1.6%** for single-shot and **0.1%** for the agent, with no loss in accuracy.
  - *Source:* `baseline-v2-gptoss.md` vs `final-gptoss.md`.

- **Iteration 2, the agent.** Traces showed the agent answered without tools (0.01 calls per case), and once forced to investigate it searched only for symptoms. Adding a `find_changes` tool and a "check what changed first" instruction raised large-incident accuracy from **33% (single-shot) to 81%**, at about 1.7× the latency. On incidents that fit in the context window it matched single-shot (90% vs 91%), so the agent is reserved for large incidents.
  - *Source:* `final-large-gptoss.md`. With 12 cases the intervals are wide: 14–61% vs 52–94%.

- **Model comparison on the same eval and GPU.** gpt-oss-120b reached **91% vs 80%** for Qwen3-30B-A3B (single-shot), with 85% vs 69% on hard cases. Qwen was 28% faster but followed a prompt injection in 6% of adversarial cases as an agent, so I chose the larger model and kept injection cases in the CI eval for every model change.
  - *Source:* `model-comparison.md`.

- **Safety under adversarial input.** Across 32 injection and secret-leak cases over 3 runs, all modes had **0% injection-follow and 0% secret leakage**. Redaction runs before every model call and tool output, and untrusted logs are delimited in the prompt.

- Trained a **logistic-regression deployment-risk model** (IRLS in numpy, feature parity between training and serving, per-feature explanations). On synthetic data it beats the rule baseline (ROC-AUC 0.79 vs 0.68).
  - Mined 2,117 real Next.js PRs with revert labels and found only 12 positives (0.6%), too few to evaluate. Say "synthetic", and use the label-scarcity result as the honest real-data story.

## Platform / backend bullets

- Built a deployment-risk platform (Spring Boot 3.5 / Java 21, FastAPI, Next.js, PostgreSQL, RabbitMQ) that ingests **signed GitHub webhooks** (HMAC-SHA256, delivery-ID idempotency) for workflow runs, pull requests and deployment statuses. It re-scores deployments when late CI failures arrive.
- Added **retry with exponential backoff and a dead-letter queue** to the async analysis pipeline. This was verified end to end against live RabbitMQ.
- Replaced three diverging copies of the risk rules with one versioned spec, enforced by contract tests in Java and Python.

## Interview framing

- **"Why is your agent not better than single-shot?"** Because tools can only help when they reveal something the prompt doesn't contain. When the incident fits in context, the agent adds cost without accuracy. The large-incident set isolates the case where it wins.
- **"How do you know the improvement is real?"** Three runs per mode, run-to-run standard deviation, and a Wilson interval over cases. The early 8/8 result is the example of a number that didn't survive a bigger set.
- **"What stops the model from inventing evidence?"** It can't quote freely. It cites line IDs, and the server inserts the real line.
- **"What happened with real data?"** Revert labels are 0.6% positive in one repository. That isn't enough to evaluate on, so the plan is to pool repositories and add weaker labels.

## Do not claim

- Production traffic, users, uptime, or incident-reduction percentages. None are measured.
- Real-world risk-model accuracy.
- Any LLM number not traceable to a file in `ai-service/evals/reports/`.
- That the eval set is real incident data. It is synthetic, with hand-authored signatures.
