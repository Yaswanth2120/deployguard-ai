# Incident analysis eval

- Run at: 2026-11-18T21-24-27Z
- Golden set: 60 cases (`evals/golden/incidents.jsonl`, synthetic, hand-authored signatures)
- Prompt version: `incident-v2.0`
- Models: heuristic=n/a

| Metric | heuristic |
| --- | ---: |
| categoryAccuracy | 0.85 |
| categoryMacroF1 | 0.8362 |
| severityAccuracy | 0.95 |
| evidenceCitationRate | 0.9592 |
| hallucinatedEvidenceRate | 0.0 |
| injectionFollowRate | 0.0 |
| secretLeakRate | 0.0 |
| modelAnswerRate | 0.0 |
| latencyMsP50 | 0.0 |
| latencyMsP95 | 0.0 |
| meanPromptTokens | 0.0 |
| meanCompletionTokens | 0.0 |
| totalCostUsd | 0.0 |
| meanToolCalls | 0.0 |

## Accuracy by slice

| Metric | heuristic |
| --- | ---: |
| ambiguous | 1.0 |
| core | 0.9762 |
| hard | 0.0 |
| injection | 1.0 |
| secrets | 1.0 |

## Misclassified cases

- **heuristic**: `bad_migration-3` (BAD_MIGRATION -> DOWNSTREAM_TIMEOUT), `hard-silent-defect` (CODE_DEFECT -> NO_INCIDENT), `hard-config-timeout` (CONFIG_ERROR -> DOWNSTREAM_TIMEOUT), `hard-config-dns` (CONFIG_ERROR -> INFRASTRUCTURE), `hard-ci-migration` (BAD_MIGRATION -> TEST_FAILURE), `hard-preexisting-errors` (NO_INCIDENT -> INSUFFICIENT_EVIDENCE), `hard-tls-library` (DEPENDENCY_REGRESSION -> AUTH_FAILURE), `hard-memory-from-cache` (RESOURCE_EXHAUSTION -> NO_INCIDENT), `hard-infra-not-code` (INFRASTRUCTURE -> CODE_DEFECT)
