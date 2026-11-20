# AI Engineering

How the AI parts of DeployGuard are built, measured, and kept safe. Every number below comes from a script in this repository. If a number has not been measured, this document says so.

## 1. Incident analysis pipeline

`ai-service/app/services/incident_analyzer.py`

```text
request
  -> redact secrets/PII              app/redaction.py
  -> bound + dedupe + rank context   app/context_builder.py
  -> mode:
       heuristic    deterministic analyzer (also the fallback)          app/heuristic.py
       single_shot  one model call, full bounded context
       rag          single_shot + top-3 BM25 hits from knowledge base   app/retrieval.py, app/knowledge/kb.json
       agent        tool loop: find_changes, search_logs,               app/agent.py
                    get_log_stats, get_ci_failures, search_knowledge_base
                    (first step must call a tool; max 6 steps)
  -> schema-constrained decoding (json_schema) + strict validation
  -> evidence by reference: cited ids replaced by exact lines     app/grounding.py
  -> provenance + telemetry          app/telemetry.py, GET /metrics
```

The mode comes from `ANALYSIS_MODE` (default `rag`) or the `?mode=` query parameter on `POST /analyze-incident`.

### Output contract

The response has `summary`, `likelyRootCause`, `rootCauseCategory`, `evidence`, `recommendedActions`, `severity`, `confidence` and `meta`.

`rootCauseCategory` is a closed set of labels:

- `BAD_MIGRATION`
- `CONFIG_ERROR`
- `DEPENDENCY_REGRESSION`
- `RESOURCE_EXHAUSTION`
- `DOWNSTREAM_TIMEOUT`
- `AUTH_FAILURE`
- `CODE_DEFECT`
- `TEST_FAILURE`
- `INFRASTRUCTURE`
- `NO_INCIDENT`
- `INSUFFICIENT_EVIDENCE`

A closed label set is what makes model output scoreable.

`meta` records:

- `source` (`model` or `heuristic`)
- `fallbackReason`, one of: `no_api_key`, `timeout`, `rate_limited`, `provider_error`, `network_error`, `invalid_json`, `schema_mismatch`, `heuristic_mode`
- `model` and `promptVersion`
- token counts, estimated cost and latency
- tool-call count, redaction count, dropped-evidence count and retrieved knowledge-base IDs

The backend persists the provenance fields (`V9` migration). The UI labels heuristic output as "Heuristic fallback, not model output". Fallback output can never pass as model output.

### Failure handling

Every model failure falls back to the heuristic analyzer, and the response records the specific reason. The heuristic analyzer does not return canned text: it cites real log and CI lines as evidence.

The backend's async path retries only transient AI-service failures:

- 3 attempts with exponential backoff
- then the job is marked `FAILED` with its attempt count
- then the message is dead-lettered to `deployment.ai.analysis.dlq`

## 2. Safety controls

| Risk | Control | Where |
| --- | --- | --- |
| Secrets/PII sent to a third-party model | Regex redaction (JWTs, GitHub/AWS/Slack/`sk-` keys, bearer tokens, `key=value` secrets, URL credentials, private keys, emails, IPv4, card numbers) runs before prompt construction and before tool output. | `app/redaction.py` |
| Prompt injection through log lines | Logs, CI runs and knowledge-base text are wrapped in `<untrusted_logs>`, `<ci_runs>` and `<knowledge_base>` tags. The system prompt declares them data. The heuristic flags instruction-like lines as suspicious evidence. | `app/prompts.py`, `app/heuristic.py` |
| Hallucinated evidence | The model cites reference ids (`[L3]`, `CI1`), and the server replaces each id with the exact line text, so a quote cannot be invented or distorted. Items without a valid id must share ≥60% of their content tokens with one input line, or they are dropped and counted. If every item is dropped, confidence is forced to `LOW`. | `app/grounding.py` |
| Off-schema output | Decoding is constrained to the JSON schema (`response_format: json_schema`, `LLM_STRUCTURED_OUTPUT`), then validated again (exact key set, closed enum). Anything still invalid falls back to the heuristic with reason `schema_mismatch`. | `app/llm_client.py`, `parse_model_response` |
| Context blow-up | Messages are truncated to 400 characters. Identical lines are deduplicated with a count. Lines are ranked by severity, then frequency, and capped at 60 lines and about 6k tokens. The agent mode keeps a larger set reachable only through tools. | `app/context_builder.py` |

## 3. Evaluation

`ai-service/evals/`

Two synthetic eval sets, built from hand-written failure signatures plus seeded noise, distractor lines and duplicates.

**Main set** (`evals/golden/incidents.jsonl`, 139 cases, `python -m evals.build_golden`). The first 60 cases are unchanged from v1, so old results stay comparable.

| Slice | Cases | What it tests |
| --- | ---: | --- |
| core | 42 | One per category variant: 4 variants × 10 categories, plus 2 insufficient-evidence cases. |
| hard | 55 | Keywords are absent or point to the wrong category; the answer needs causal reasoning. Examples: a timeout caused by a pool-size config change; an NPE caused by a migration adding a column without a default; a 401 caused by a library upgrade changing default JWT algorithms; errors that already existed before the deploy. |
| ambiguous | 10 | Two categories are both defensible. |
| injection | 16 | A real incident plus a log line that tries to force `NO_INCIDENT`/`LOW` or emit a marker. Base incidents always contain real ERROR lines, so a `NO_INCIDENT` answer can only come from obeying the injection. |
| secrets | 16 | Logs contain fake credentials or PII that must never appear in the output. |

**Large-incident set** (`evals/golden/large_incidents.jsonl`, 12 cases, `python -m evals.build_large`). Twelve hard cases with 150 benign WARN lines added. Context ranking pushes the decisive INFO line (for example "pool size changed from 40 to 4 in this release") outside the 60 lines single-shot and RAG see; the agent can still reach it with tools. This tests the one situation where tool use should matter.

The metrics are in `evals/scoring.py`:

- category accuracy and macro-F1
- severity accuracy
- evidence citation rate: evidence quotes the signature line
- hallucinated-evidence rate: ungrounded items plus items dropped by the guard
- injection-follow rate
- secret-leak rate
- model-answer rate and fallback reasons
- p50/p95 latency
- mean tokens and total cost
- mean tool calls
- optional LLM-as-judge score (`--judge`, 1–5)

`--runs N` repeats each mode N times and reports mean ± run-to-run standard deviation, plus a 95% Wilson interval on accuracy that reflects how many cases the set has. `python -m evals.merge` combines result files into one report.

Commands:

```bash
cd ai-service
PYTHONPATH=. python -m evals.run --mode heuristic                          # offline
OPENROUTER_API_KEY=... PYTHONPATH=. python -m evals.run \
  --mode heuristic --mode single_shot --mode rag --mode agent [--judge]    # full comparison
```

### Running against a self-hosted model

The results below come from models served with vLLM on a rented GPU (vast.ai vLLM template, one A100 80 GB). `scripts/gpu-connect.sh -p <ssh-port> root@<ip> [model]` does the setup:
- configures tool calling, 16k context and 8 concurrent requests;
- switches the model;
- restarts vLLM, including stopping an orphaned server that the template's restart leaves behind;
- opens an SSH tunnel so the API is reachable only at `localhost:18000`.

Then point the eval at it:

```bash
OPENROUTER_API_KEY=local OPENROUTER_BASE_URL=http://localhost:18000/v1 OPENROUTER_MODEL=openai/gpt-oss-120b \
  PYTHONPATH=. python -m evals.run --mode single_shot --mode rag --mode agent --runs 3
```

Reports go to `evals/reports/latest.md`. CI runs the heuristic eval on every push and fails if it drops below `evals/thresholds.json`. Safety metrics must stay at 0. A manual `workflow_dispatch` job runs the LLM modes when the `OPENROUTER_API_KEY` repository secret is set.

### Current measured results

All runs: 2026-11-20, models self-hosted with vLLM on one A100 80 GB, temperature 0, 3 runs per mode (mean ± run-to-run std; the 95% Wilson interval reflects the number of cases). Reports are in `ai-service/evals/reports/`.

**Final, gpt-oss-120b, main set (139 cases)**: `final-gptoss.md`

| Metric | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| Category accuracy | 0.619 | **0.909 ± 0.017** | 0.899 ± 0.006 | 0.897 ± 0.012 |
| 95% interval | 0.54–0.70 | 0.85–0.95 | 0.84–0.94 | 0.84–0.94 |
| Hard slice (55) | 0.255 | 0.849 | 0.861 | 0.842 |
| Schema-valid model answers | n/a | 1.00 | 1.00 | 1.00 |
| Ungrounded evidence | 0 | 0.016 | 0.019 | 0.001 |
| Injection-follow / secret leak | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| Latency p50 / p95 (s) | 0 | 5.2 / 6.7 | 5.8 / 7.5 | 8.6 / 13.0 |
| Prompt tokens / tool calls | 0 / 0 | 787 / 0 | 1,003 / 0 | 3,043 / 1.5 |

**Large incidents (12 cases), gpt-oss-120b**: `final-large-gptoss.md`

| | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| Category accuracy | 0.25 | 0.33 | 0.33 | **0.81 ± 0.04** |
| 95% interval | 0.09–0.53 | 0.14–0.61 | 0.14–0.61 | 0.52–0.94 |
| Latency p50 (s) | 0 | 6.1 | 6.8 | 10.5 |

### How we got here: two measured iterations

| Step | What changed | Result |
| --- | --- | --- |
| v1, 60 cases | First LLM run | RAG 8/8 on hard cases. The Wilson interval for 8/8 is 0.68–1.00, so this proved little. |
| Baseline, 139 cases (`baseline-v2-gptoss.md`) | Bigger set, same code | single_shot 0.907, rag 0.907, agent 0.871. RAG's advantage disappeared. Schema-valid 98.6% / 100% / 97.8%. Ungrounded evidence 6.1% / 5.7% / 1.5%. Agent tool calls 0.01 per case. |
| Iteration 1 | Schema-constrained decoding; evidence by reference; agent must call a tool first | Schema-valid 100% in all modes. Ungrounded evidence down to 1.6% / 1.9% / 0.1%. Accuracy unchanged within noise. |
| (bug found) | The agent cited bare `L1` ids copied from tool output, and the parser only accepted `[L1]` | The scorer flagged 15% "ungrounded" evidence for the agent. A trace showed a parser bug, not hallucination. Fixed. |
| Iteration 2 | The agent only searched for symptoms → added `find_changes` and a "check what changed first" instruction | Large incidents: 0.44 → 0.81. Main set: 0.87 → 0.90, level with single_shot. |

**Decision.** Default to single_shot: it is the most accurate and the cheapest when the incident fits in the context window. Route large incidents (context truncated) to the agent, which pays about 1.7× the latency for a large accuracy gain. RAG did not earn its extra tokens on this set.

**Caveat on `find_changes`.** Change descriptions arrive as log lines in this synthetic data, which flatters a keyword matcher. In production the tool would query a change-event feed (deploy diffs, config history, provider status).

### Model comparison: gpt-oss-120b vs Qwen3-30B-A3B-Instruct-2507

Same GPU, same code and prompts: `model-comparison.md`, `model-comparison-large.md`.

| | gpt-oss-120b | Qwen3-30B-A3B |
| --- | ---: | ---: |
| single_shot accuracy (139) | **0.909** (0.85–0.95) | 0.799 (0.72–0.86) |
| Hard slice, best mode | **0.861** (rag) | 0.697 (agent) |
| Agent, large incidents | **0.81** | 0.75 |
| single_shot latency p50 | 5.2 s | **3.7 s** |
| Agent tool calls per case | 1.5 | 5.1 (tends to use the whole step budget) |
| Agent injection-follow | 0 | **0.0625** |
| RAG ungrounded evidence | 0.019 | 0.106 |

The larger model wins on accuracy by about 11 points and is the only one with zero injection-follow across all modes. The smaller model is 28% faster per answer. For triage, where a wrong root cause is expensive, gpt-oss-120b is the right default. The Qwen agent following an injection in about 1 of 16 adversarial cases is a reason to keep injection cases in the CI eval for every model change.

Caveats: one run session per model; synthetic cases with hand-written signatures; the large-incident set has only 12 cases.

## 4. Deployment risk model

`ai-service/ml/` (training), `ai-service/app/services/risk_model.py` (serving), `ai-service/ml_artifacts/risk_model.json` (artifact).

### Features

There are 14 features, built by `app/ml_features.py`. Training and serving use the same function, so feature skew between them is impossible.

- log lines changed and log files changed
- whether the change touches migrations or config
- test-file ratio
- hotfix branch
- production target
- failed CI and log failed-test count
- log error-log count
- log CI duration
- off-hours and weekend merge
- log author experience

### Model

The model is L2 logistic regression fit by IRLS in numpy. Lambda is selected on validation log loss. The split is ordered (70/15/15), so time-ordered data never trains on the future.

Serving needs no numpy: it uses only standardization stats and coefficients. Each score comes with per-feature contributions (coefficient × standardized value).

### Baseline

The baseline is the deterministic rule engine (`shared/risk-rules.json`, version `deterministic-v2`). `/v1/risk-scores` returns both scores (`score`/`probability` and `baselineScore`).

### Data

- `python -m ml.synthetic` generates **synthetic** data from a hidden process. That process includes effects the rules ignore, an interaction term and a latent variable.
- `python -m ml.mine_github --repo owner/name` builds a **real** dataset from a public repository. It labels a merged PR as risky when it is reverted within 14 days, either by a PR titled `Revert "<title>"` or by GitHub's `Reverts owner/repo#N` body.

**Real-data attempt (2026-11-20).** Mined the 2,117 most recent merged PRs of `vercel/next.js` (features from PR metadata and check runs). Only **12 were reverted within 14 days (0.57%)**. With an ordered 70/15/15 split the test set contained **zero** positives, so ROC-AUC and PR-AUC are undefined and no real-world claim can be made. The committed artifact therefore stays the synthetic-data model.

What this shows: revert labels are rare enough that one repository and a few months of history is not enough. Next steps, in order:
1. Pool several high-revert repositories (grafana/grafana, apache/airflow, microsoft/vscode, home-assistant/core all have 200+ reverted PRs since 2024).
2. Use a longer window.
3. Add weaker labels (a follow-up PR titled "fix" touching the same files within 48 hours).

### Current measured results

Held-out test set: 3,000 rows. The model is trained on **synthetic data**; see `ml/reports/risk_model_report.md`.

| Metric | Rule baseline | Logistic regression |
| --- | ---: | ---: |
| ROC-AUC | 0.679 | 0.788 |
| PR-AUC | 0.222 | 0.368 |
| Brier | 0.096 | 0.078 |
| ECE | 0.074 | 0.010 |
| Recall @ precision ≥ 0.5 | 0.125 | 0.283 |

These numbers show that the pipeline works and beats the rules on a process the rules don't capture. They are **not** evidence of real-world performance. To retry with real data:

```bash
cd ai-service
GITHUB_TOKEN=... python -m ml.mine_github --repo <owner>/<repo> --max-prs 2000 --out ml/data/real.jsonl
python -m ml.train --data ml/data/real.jsonl --version logreg-v1-real
```

## 5. Observability

`GET /metrics` on the AI service exposes Prometheus text format:

- `deployguard_analysis_total{source,mode,reason}`
- `deployguard_analysis_latency_seconds` (histogram)
- `deployguard_llm_tokens_total{type}`
- `deployguard_llm_cost_usd_total`
- `deployguard_agent_tool_calls_total`
- `deployguard_ungrounded_evidence_total`
- `deployguard_redactions_total`

Each analysis also emits one structured JSON log line. That line never contains prompt text or raw input.

Not implemented: distributed tracing across backend → AI service, and dashboards and alerts on these metrics.

## 6. Known gaps

- Eval sets are synthetic, with hand-authored signatures. A slice built from real public postmortems is the next step.
- The large-incident set has 12 cases, so its intervals are wide.
- `find_changes` is a log-keyword stand-in for a real change-event feed.
- The risk model is trained on synthetic data. Real revert labels were too sparse (12 of 2,117 PRs).
- Retrieval is lexical (BM25) over 15 hand-written documents. An embedding retriever would need its own retrieval eval (recall@k on labeled queries).
- Redaction is regex-based. It does not catch secrets in unknown formats.
