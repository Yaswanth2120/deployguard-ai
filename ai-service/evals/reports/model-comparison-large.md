# Incident analysis eval

- Run at: merged from `2026-11-20T20-26-47Z.json`, `2026-11-20T20-29-23Z.json`, `2026-11-20T20-58-17Z.json`
- Eval set: 12 cases (`evals/golden/incidents.jsonl`, synthetic)
- Prompt version: `incident-v3.1`
- Models: heuristic=n/a, single_shot@gpt-oss-120b=openai/gpt-oss-120b, rag@gpt-oss-120b=openai/gpt-oss-120b, agent@gpt-oss-120b=openai/gpt-oss-120b, single_shot@Qwen3-30B-A3B-Instruct-2507=Qwen/Qwen3-30B-A3B-Instruct-2507, rag@Qwen3-30B-A3B-Instruct-2507=Qwen/Qwen3-30B-A3B-Instruct-2507, agent@Qwen3-30B-A3B-Instruct-2507=Qwen/Qwen3-30B-A3B-Instruct-2507

| Metric | heuristic | single_shot@gpt-oss-120b | rag@gpt-oss-120b | agent@gpt-oss-120b | single_shot@Qwen3-30B-A3B-Instruct-2507 | rag@Qwen3-30B-A3B-Instruct-2507 | agent@Qwen3-30B-A3B-Instruct-2507 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| categoryAccuracy | 0.25 | 0.3333 | 0.3333 | 0.8055 ± 0.0393 | 0.2222 ± 0.0393 | 0.2222 ± 0.0393 | 0.75 |
| categoryMacroF1 | 0.0667 | 0.2351 ± 0.0042 | 0.2457 ± 0.0061 | 0.723 ± 0.0438 | 0.1319 ± 0.015 | 0.1241 ± 0.0249 | 0.6522 ± 0.0716 |
| severityAccuracy | 1.0 | 1.0 | 1.0 | 1.0 | 0.9722 ± 0.0393 | 0.9445 ± 0.0393 | 1.0 |
| evidenceCitationRate | 0.0 | 0.4167 | 0.4167 | 1.0 | 0.3333 | 0.3333 | 1.0 |
| hallucinatedEvidenceRate | 0.0 | 0.0 | 0.0 | 0.0 | 0.008 ± 0.0023 | 0.0086 ± 0.002 | 0.0663 ± 0.0347 |
| injectionFollowRate | None | None | None | None | None | None | None |
| secretLeakRate | None | None | None | None | None | None | None |
| modelAnswerRate | 0.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 | 1.0 |
| latencyMsP50 | 5.0 | 6109.0 ± 628.3518 | 6781.6667 ± 106.6625 | 10531.6667 ± 1350.712 | 5338.6667 ± 526.3626 | 5238.0 ± 175.3245 | 9727.0 ± 665.0118 |
| latencyMsP95 | 5.0 | 7809.0 ± 762.7966 | 8400.0 ± 851.8533 | 14595.6667 ± 1131.614 | 7564.6667 ± 2397.3277 | 7926.6667 ± 1387.7839 | 11496.6667 ± 539.6643 |
| meanPromptTokens | 0.0 | 1904.2 | 2126.7 | 6857.0667 ± 532.5219 | 2032.4 | 2256.2 | 19731.4333 ± 51.7154 |
| meanCompletionTokens | 0.0 | 441.0667 ± 13.4723 | 529.1667 ± 12.8011 | 748.8667 ± 52.5693 | 331.1667 ± 29.1764 | 340.2 ± 3.6851 | 394.0667 ± 3.45 |
| totalCostUsd | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 | 0.0 |
| meanToolCalls | 0.0 | 0.0 | 0.0 | 1.89 ± 0.198 | 0.0 | 0.0 | 5.33 |
| categoryAccuracyCI95 | [0.089, 0.532] | [0.138, 0.609] | [0.138, 0.609] | [0.523, 0.94] | [0.074, 0.505] | [0.074, 0.505] | [0.468, 0.911] |
| runs | 1 | 3 | 3 | 3 | 3 | 3 | 3 |

## Accuracy by slice

| Metric | heuristic | single_shot@gpt-oss-120b | rag@gpt-oss-120b | agent@gpt-oss-120b | single_shot@Qwen3-30B-A3B-Instruct-2507 | rag@Qwen3-30B-A3B-Instruct-2507 | agent@Qwen3-30B-A3B-Instruct-2507 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| large | 0.25 | 0.3333 | 0.3333 | 0.8055 | 0.2222 | 0.2222 | 0.75 |

## Misclassified cases

Count after the id = number of runs in which it was wrong.

- **heuristic**: `large-h-mig-npe` (BAD_MIGRATION -> CONFIG_ERROR) x1, `large-h-mig-enum` (BAD_MIGRATION -> CONFIG_ERROR) x1, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x1, `large-h-down-preexisting` (DOWNSTREAM_TIMEOUT -> CONFIG_ERROR) x1, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x1, `large-h-infra-az` (INFRASTRUCTURE -> CONFIG_ERROR) x1, `large-h-infra-registry` (INFRASTRUCTURE -> CONFIG_ERROR) x1
- **single_shot@gpt-oss-120b**: `large-h-mig-npe` (BAD_MIGRATION -> CODE_DEFECT) x3, `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> AUTH_FAILURE) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3
- **rag@gpt-oss-120b**: `large-h-mig-npe` (BAD_MIGRATION -> CODE_DEFECT) x3, `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> AUTH_FAILURE) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CODE_DEFECT) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3
- **agent@gpt-oss-120b**: `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x1, `large-h-infra-az` (INFRASTRUCTURE -> DOWNSTREAM_TIMEOUT) x1, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3, `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x2
- **single_shot@Qwen3-30B-A3B-Instruct-2507**: `large-h-mig-npe` (BAD_MIGRATION -> CODE_DEFECT) x3, `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-cfg-region` (CONFIG_ERROR -> NO_INCIDENT) x1, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> RESOURCE_EXHAUSTION) x3, `large-h-down-preexisting` (DOWNSTREAM_TIMEOUT -> RESOURCE_EXHAUSTION) x3, `large-h-auth-scope` (AUTH_FAILURE -> DEPENDENCY_REGRESSION) x3, `large-h-infra-az` (INFRASTRUCTURE -> CONFIG_ERROR) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3
- **rag@Qwen3-30B-A3B-Instruct-2507**: `large-h-cfg-pool` (CONFIG_ERROR -> RESOURCE_EXHAUSTION) x3, `large-h-cfg-ratelimit` (CONFIG_ERROR -> BAD_MIGRATION) x3, `large-h-dep-jwt` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-date` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> DOWNSTREAM_TIMEOUT) x3, `large-h-down-preexisting` (DOWNSTREAM_TIMEOUT -> DEPENDENCY_REGRESSION) x3, `large-h-auth-scope` (AUTH_FAILURE -> DEPENDENCY_REGRESSION) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x3, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3, `large-h-cfg-region` (CONFIG_ERROR -> INSUFFICIENT_EVIDENCE) x1
- **agent@Qwen3-30B-A3B-Instruct-2507**: `large-h-auth-scope` (AUTH_FAILURE -> CONFIG_ERROR) x3, `large-h-infra-az` (INFRASTRUCTURE -> CODE_DEFECT) x1, `large-h-infra-registry` (INFRASTRUCTURE -> AUTH_FAILURE) x3, `large-h-dep-timeout` (DEPENDENCY_REGRESSION -> CONFIG_ERROR) x2
