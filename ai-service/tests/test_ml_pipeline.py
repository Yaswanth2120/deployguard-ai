import numpy as np

from ml.metrics import average_precision, expected_calibration_error, roc_auc
from ml.mine_github import classify_files, find_reverted
from ml.synthetic import generate
from ml.train import fit_logreg, predict


def test_roc_auc_and_ap_known_values() -> None:
    y = np.array([0, 0, 1, 1])
    p = np.array([0.1, 0.4, 0.35, 0.8])
    assert roc_auc(y, p) == 0.75
    assert abs(average_precision(y, p) - 0.8333) < 1e-3
    assert roc_auc(np.array([0, 1]), np.array([0.5, 0.5])) == 0.5


def test_ece_is_zero_for_perfect_calibration() -> None:
    y = np.array([0, 1] * 50)
    assert expected_calibration_error(y, np.full(100, 0.5)) == 0.0


def test_logreg_recovers_signal() -> None:
    rng = np.random.default_rng(0)
    x = rng.normal(size=(4000, 2))
    y = (rng.random(4000) < 1 / (1 + np.exp(-(2 * x[:, 0] - 1)))).astype(float)
    w = fit_logreg(x, y, lam=0.0)
    assert abs(w[1] - 2) < 0.25 and abs(w[2]) < 0.15
    assert roc_auc(y, predict(x, w)) > 0.8


def test_synthetic_generator_is_deterministic() -> None:
    assert generate(50, seed=1) == generate(50, seed=1)


def test_revert_labeling() -> None:
    prs = [
        {"number": 1, "title": "Add cache", "body": "", "merged_at": "2026-01-01T00:00:00Z"},
        {"number": 2, "title": 'Revert "Add cache"', "body": "", "merged_at": "2026-01-03T00:00:00Z"},
        {"number": 3, "title": "Change pool", "body": "", "merged_at": "2026-01-01T00:00:00Z"},
        {"number": 4, "title": "Undo", "body": "Reverts acme/api#3", "merged_at": "2026-03-01T00:00:00Z"},
        {"number": 5, "title": "Bump lib", "body": "", "merged_at": "2026-01-05T00:00:00Z"},
        {"number": 6, "title": "x", "body": "Reverts acme/api#5", "merged_at": "2026-01-06T00:00:00Z"},
    ]
    assert find_reverted(prs, window_days=14) == {1, 5}


def test_file_classification() -> None:
    result = classify_files(["db/migration/V9__x.sql", "src/app/config/settings.yaml", "tests/test_app.py", "src/App.java"])
    assert result == {"touchesMigrations": True, "touchesConfig": True, "testFilesChanged": 1}
