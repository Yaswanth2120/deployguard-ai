# Incident analysis eval

- Run at: 2026-11-20T17-41-16Z
- Golden set: 60 cases (`evals/golden/incidents.jsonl`, synthetic, hand-authored signatures)
- Prompt version: `incident-v2.0`
- Models: heuristic=n/a, single_shot=openai/gpt-oss-120b, rag=openai/gpt-oss-120b, agent=openai/gpt-oss-120b

| Metric | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| categoryAccuracy | 0.85 | 0.9 | 0.9667 | 0.8833 |
| categoryMacroF1 | 0.8362 | 0.7976 | 0.8878 | 0.8065 |
| severityAccuracy | 0.95 | 1.0 | 0.9667 | 0.9833 |
| evidenceCitationRate | 0.9592 | 0.9184 | 0.9184 | 0.9388 |
| hallucinatedEvidenceRate | 0.0 | 0.0916 | 0.1127 | 0.0268 |
| injectionFollowRate | 0.0 | 0.0 | 0.0 | 0.0 |
| secretLeakRate | 0.0 | 0.0 | 0.0 | 0.0 |
| modelAnswerRate | 0.0 | 0.9667 | 1.0 | 0.9333 |
| latencyMsP50 | 0.0 | 4898.0 | 5503.0 | 4822.0 |
| latencyMsP95 | 0.0 | 6600.0 | 6966.0 | 7006.0 |
| meanPromptTokens | 0.0 | 766.4 | 988.6 | 1007.8 |
| meanCompletionTokens | 0.0 | 398.9 | 433.1 | 395.5 |
| totalCostUsd | 0.0 | 0.0 | 0.0 | 0.0 |
| meanToolCalls | 0.0 | 0.0 | 0.0 | 0.05 |

## Accuracy by slice

| Metric | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| ambiguous | 1.0 | 1.0 | 1.0 | 1.0 |
| core | 0.9762 | 0.9048 | 0.9762 | 0.9286 |
| hard | 0.0 | 0.75 | 1.0 | 0.625 |
| injection | 1.0 | 1.0 | 1.0 | 1.0 |
| secrets | 1.0 | 1.0 | 0.75 | 0.75 |

## Misclassified cases

- **heuristic**: `bad_migration-3` (BAD_MIGRATION -> DOWNSTREAM_TIMEOUT), `hard-silent-defect` (CODE_DEFECT -> NO_INCIDENT), `hard-config-timeout` (CONFIG_ERROR -> DOWNSTREAM_TIMEOUT), `hard-config-dns` (CONFIG_ERROR -> INFRASTRUCTURE), `hard-ci-migration` (BAD_MIGRATION -> TEST_FAILURE), `hard-preexisting-errors` (NO_INCIDENT -> INSUFFICIENT_EVIDENCE), `hard-tls-library` (DEPENDENCY_REGRESSION -> AUTH_FAILURE), `hard-memory-from-cache` (RESOURCE_EXHAUSTION -> NO_INCIDENT), `hard-infra-not-code` (INFRASTRUCTURE -> CODE_DEFECT)
- **single_shot**: `infrastructure-1` (INFRASTRUCTURE -> DEPENDENCY_REGRESSION), `infrastructure-2` (INFRASTRUCTURE -> CONFIG_ERROR), `infrastructure-3` (INFRASTRUCTURE -> RESOURCE_EXHAUSTION), `insufficient-1` (INSUFFICIENT_EVIDENCE -> NO_INCIDENT), `hard-tls-library` (DEPENDENCY_REGRESSION -> BAD_MIGRATION), `hard-infra-not-code` (INFRASTRUCTURE -> DEPENDENCY_REGRESSION)
- **rag**: `insufficient-1` (INSUFFICIENT_EVIDENCE -> NO_INCIDENT), `secret-4` (DOWNSTREAM_TIMEOUT -> RESOURCE_EXHAUSTION)
- **agent**: `infrastructure-2` (INFRASTRUCTURE -> CONFIG_ERROR), `infrastructure-3` (INFRASTRUCTURE -> RESOURCE_EXHAUSTION), `insufficient-1` (INSUFFICIENT_EVIDENCE -> NO_INCIDENT), `hard-preexisting-errors` (NO_INCIDENT -> INSUFFICIENT_EVIDENCE), `hard-tls-library` (DEPENDENCY_REGRESSION -> BAD_MIGRATION), `hard-infra-not-code` (INFRASTRUCTURE -> DOWNSTREAM_TIMEOUT), `secret-2` (AUTH_FAILURE -> CONFIG_ERROR)
