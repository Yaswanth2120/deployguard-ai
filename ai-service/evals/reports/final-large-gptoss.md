# Incident analysis eval

- Run at: merged from `2026-11-20T20-26-47Z.json`, `2026-11-20T20-29-23Z.json`
- Eval set: 12 cases (`evals/golden/incidents.jsonl`, synthetic)
- Prompt version: `incident-v3.1`
- Models: heuristic=n/a, single_shot=openai/gpt-oss-120b, rag=openai/gpt-oss-120b, agent=openai/gpt-oss-120b

| Metric | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| categoryAccuracy | 0.25 | 0.3333 | 0.3333 | 0.8055 ± 0.0393 |
| categoryMacroF1 | 0.0667 | 0.2351 ± 0.0042 | 0.2457 ± 0.0061 | 0.723 ± 0.0438 |
| severityAccuracy | 1.0 | 1.0 | 1.0 | 1.0 |
| evidenceCitationRate | 0.0 | 0.4167 | 0.4167 | 1.0 |
| hallucinatedEvidenceRate | 0.0 | 0.0 | 0.0 | 0.0 |
| injectionFollowRate | None | None | None | None |
| secretLeakRate | None | None | None | None |
| modelAnswerRate | 0.0 | 1.0 | 1.0 | 1.0 |
| latencyMsP50 | 5.0 | 6109.0 ± 628.3518 | 6781.6667 ± 106.6625 | 10531.6667 ± 1350.712 |
| latencyMsP95 | 5.0 | 7809.0 ± 762.7966 | 8400.0 ± 851.8533 | 14595.6667 ± 1131.614 |
| meanPromptTokens | 0.0 | 1904.2 | 2126.7 | 6857.0667 ± 532.5219 |
| meanCompletionTokens | 0.0 | 441.0667 ± 13.4723 | 529.1667 ± 12.8011 | 748.8667 ± 52.5693 |
| totalCostUsd | 0.0 | 0.0 | 0.0 | 0.0 |
| meanToolCalls | 0.0 | 0.0 | 0.0 | 1.89 ± 0.198 |
| categoryAccuracyCI95 | [0.089, 0.532] | [0.138, 0.609] | [0.138, 0.609] | [0.523, 0.94] |
| runs | 1 | 3 | 3 | 3 |

## Accuracy by slice

| Metric | heuristic | single_shot | rag | agent |
| --- | ---: | ---: | ---: | ---: |
| large | 0.25 | 0.3333 | 0.3333 | 0.8055 |

## Misclassified cases

Count after the id = number of runs in which it was wrong.

- **heuristic**: `large-h-mig-npe` (BAD_MIGRATION -> CONFIG_ERROR) x1, `large-h-mig-enum` (BAD_MIGRATION -> CONFIG_ERROR) x1, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-down-preexisting` (DOWNSTREAM_TIMEOUT -> CONFIG_ERROR) x1, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x1, `large-h-infra-az` (INFRASTRUCTURE -> CONFIG_ERROR) x1, `large-h-infra-registry` (INFRASTRUCTURE -> CONFIG_ERROR) x1
- **single_shot**: `large-h-mig-npe` (BAD_MIGRATION -> CODE_DEFECT) x3, `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> AUTH_FAILURE) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3
- **rag**: `large-h-mig-npe` (BAD_MIGRATION -> CODE_DEFECT) x3, `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> AUTH_FAILURE) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CODE_DEFECT) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3
- **agent**: `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x1, `large-h-infra-az` (INFRASTRUCTURE -> DOWNSTREAM_TIMEOUT) x1, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x2
