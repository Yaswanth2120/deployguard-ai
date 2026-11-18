# Risk model report: logreg-v1

- Data source: `synthetic-v1` — **SYNTHETIC DATA. These numbers validate the pipeline, not real-world performance.**
- Rows: train 14000, validation 3000, test 3000; positive rate 0.111
- Model: L2 logistic regression (IRLS), lambda=0.0 selected on validation log loss

## Held-out test set

| Metric | Rule baseline (deterministic-v2) | Model |
| --- | ---: | ---: |
| rocAuc | 0.6789 | 0.7877 |
| prAuc | 0.2219 | 0.3678 |
| brier | 0.0964 | 0.0776 |
| logLoss | 0.5804 | 0.272 |
| ece | 0.0741 | 0.0095 |
| recallAtPrecision50 | 0.125 | 0.2829 |

Rule-baseline probabilities are `score / 100`; its Brier/ECE/log loss reflect that it was never calibrated.
ROC-AUC and PR-AUC are rank-based and compare the two fairly.

## Coefficients (standardized features)

| Feature | Coefficient |
| --- | ---: |
| log_lines_changed | +0.512 |
| failed_ci | +0.413 |
| touches_migrations | +0.389 |
| log_error_logs | +0.384 |
| log_author_experience | -0.350 |
| test_ratio | -0.350 |
| off_hours | +0.288 |
| targets_production | +0.238 |
| touches_config | +0.231 |
| weekend | +0.104 |
| log_failed_tests | +0.088 |
| hotfix_branch | +0.085 |
| log_ci_duration_min | -0.037 |
| log_files_changed | -0.007 |
